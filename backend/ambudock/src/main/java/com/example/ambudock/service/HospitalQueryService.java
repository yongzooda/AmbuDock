package com.example.ambudock.service;

import com.example.ambudock.domain.common.enums.HliLevel;
import com.example.ambudock.domain.hospital.HliSnapshot;
import com.example.ambudock.domain.hospital.Hospital;
import com.example.ambudock.domain.hospital.HospitalStatus;
import com.example.ambudock.dto.hospital.HospitalDtos;
import com.example.ambudock.repository.HliSnapshotRepository;
import com.example.ambudock.repository.HospitalRepository;
import com.example.ambudock.repository.HospitalStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HospitalQueryService {
    private final HospitalRepository hospitalRepo;
    private final HospitalStatusRepository statusRepo;
    private final HliSnapshotRepository hliRepo;
    private final HliService hliService;
    private final AppConfigService cfg;

    @Cacheable(cacheNames="hospitals", key="#lat+':'+#lon+':'+#radiusKm")
    @Transactional(readOnly=true)
    public HospitalDtos.NearbyResponse listNearby(double lat,double lon,double radiusKm){
        int soft=cfg.getInt("STALE_SOFT_SEC",300);
        int hard=cfg.getInt("STALE_HARD_SEC",3600);
        int penalty=cfg.getInt("HLI_LAG_SOFT_PENALTY",15);

        List<Object[]> rows=hospitalRepo.searchWithDistance(lat,lon);
        List<HospitalDtos.HospitalListItem> items=new ArrayList<>();
        for(Object[] r:rows){
            Hospital h=(Hospital)r[0]; double distance=((Number)r[1]).doubleValue();
            if(distance>radiusKm) break;

            HospitalStatus latest=statusRepo.findLatestByHpid(h.getHpid(), PageRequest.of(0,1))
                    .stream().findFirst().orElse(null);

            HliSnapshot snap=null; HliService.Computed comp=null;
            if(latest!=null){
                snap=hliRepo.findByStatus_StatusId(latest.getStatusId()).orElse(null);
                if(snap==null) comp=hliService.compute(latest, soft, hard, penalty);
            }

            HospitalDtos.HliSummary summary=null;
            if(snap!=null){
                summary=HospitalDtos.HliSummary.builder()
                        .score(snap.getScore()).level(snap.getLevel().name()).color(colorOf(snap.getLevel()))
                        .lagSec(snap.getLagSec()).staleLevel(snap.getStaleLevel().name()).updatedAt(snap.getCalcAt()).build();
            }else if(comp!=null){
                summary=HospitalDtos.HliSummary.builder()
                        .score(comp.getScore()).level(comp.getLevel().name()).color(colorOf(comp.getLevel()))
                        .lagSec(comp.getLagSec()).staleLevel(comp.getStaleLevel().name()).updatedAt(LocalDateTime.now()).build();
            }

            items.add(HospitalDtos.HospitalListItem.builder()
                    .hpid(h.getHpid()).name(h.getName()).lat(h.getLat()).lon(h.getLon())
                    .addrRoad(h.getAddrRoad()).telEmergency(h.getTelEmergency())
                    .distanceKm(distance).hli(summary).build());
        }
        return new HospitalDtos.NearbyResponse(items);
    }

    private String colorOf(HliLevel lvl){
        return switch (lvl){ case GREEN->"#16a34a"; case YELLOW->"#eab308"; case RED->"#ef4444"; case GRAY->"#9ca3af"; };
    }
}
