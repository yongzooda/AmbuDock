package com.example.ambudock.service;

import com.example.ambudock.domain.common.enums.MatchLevel;
import com.example.ambudock.domain.common.enums.Severity;
import com.example.ambudock.domain.disaster.AlertRegionLink;
import com.example.ambudock.domain.hospital.Hospital;
import com.example.ambudock.dto.disaster.DisasterDtos;
import com.example.ambudock.repository.AlertRegionLinkRepository;
import com.example.ambudock.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DisasterService {
    private final AlertRegionLinkRepository linkRepo;
    private final HospitalRepository hospitalRepo;
    private final AppConfigService cfg;

    @Transactional(readOnly=true)
    public DisasterDtos.EvaluateResponse evaluate(List<String> hpids){
        boolean blockExclude="1".equals(cfg.get("ALERT_BLOCK_EXCLUDE","1"));

        List<DisasterDtos.EvaluateItem> results=new ArrayList<>();
        for(String hpid:hpids){
            Hospital h=hospitalRepo.findById(hpid).orElse(null);
            if(h==null || h.getRegion()==null){ results.add(new DisasterDtos.EvaluateItem(hpid,false,List.of(),null)); continue; }

            List<AlertRegionLink> links=linkRepo.findActiveByRegion(h.getRegion().getRegionId());
            if(links.isEmpty()){ results.add(new DisasterDtos.EvaluateItem(hpid,false,List.of(),null)); continue; }

            links.sort(Comparator
                    .comparing((AlertRegionLink l)->l.getAlert().getSeverity()).reversed()
                    .thenComparing((AlertRegionLink l)->l.getAlert().getIssuedAt(), Comparator.reverseOrder())
                    .thenComparing(l->rank(l.getMatchLevel())));

            AlertRegionLink best=links.get(0);
            boolean excluded = best.getAlert().getSeverity()== Severity.BLOCK && blockExclude;
            List<String> badges = best.getAlert().getSeverity()==Severity.WARN
                    ? List.of("이동차질 가능("+best.getRegion().getNameFull()+")") : List.of();

            results.add(new DisasterDtos.EvaluateItem(hpid, excluded, badges, best.getAlert().getSeverity().name()));
        }
        return new DisasterDtos.EvaluateResponse(results);
    }
    private int rank(MatchLevel m){ return switch(m){ case SIGUNGU->1; case DONG->2; case SIDO->3; default->9; }; }
}

