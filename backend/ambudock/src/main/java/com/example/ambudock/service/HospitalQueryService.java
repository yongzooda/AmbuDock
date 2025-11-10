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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * HospitalQueryService
 *
 * 책임:
 * - 사용자의 위/경도와 반경을 받아 근처 병원 목록을 조회하고,
 *   각 병원의 최신 HLI 요약(HliSummary)을 붙여서 반환한다.
 *
 * 주요 설계/운영 전제:
 * - 검색 결과는 거리(작은->큰) 정렬로 반환된다고 가정한다.
 * - HLI 정보는 우선 DB에 저장된 HliSnapshot을 사용하고, 없을 경우 HliService.compute로 동적 계산한다.
 * - 캐시는 동일 (lat,lon,radiusKm) 파라미터에 대해 결과를 재활용한다.
 */
@Service
@RequiredArgsConstructor
public class HospitalQueryService {
    private final HospitalRepository hospitalRepo;
    private final HospitalStatusRepository statusRepo;
    private final HliSnapshotRepository hliRepo;
    private final HliService hliService;
    private final AppConfigService cfg;

    /**
     * listNearby
     *
     * - @Cacheable: 같은 좌표+반경 요청은 캐시를 사용한다.
     *
     * @param lat 사용자 위도 (double)
     * @param lon 사용자 경도 (double)
     * @param radiusKm 검색 반경(킬로미터)
     * @return NearbyResponse: 병원 리스트 + 각 병원의 HLI 요약
     */
    @Cacheable(cacheNames="hospitals", key="#lat+':'+#lon+':'+#radiusKm")
    @Transactional(readOnly=true)
    public HospitalDtos.NearbyResponse listNearby(double lat,double lon,double radiusKm){

        // -------------------------
        // 신선도(stale) 관련 설정값을 AppConfigService에서 로드
        // - soft: 'soft' 스테일 기준(초). 이 내이면 신선한 데이터로 판단.
        // - hard: 'hard' 스테일 기준(초). 이 이상이면 데이터 신뢰도 거의 없음.
        // - penalty: 지연(lag)에 대한 soft 패널티(점수 감소 등) 값.
        int soft=cfg.getInt("STALE_SOFT_SEC",300);
        int hard=cfg.getInt("STALE_HARD_SEC",3600);
        int penalty=cfg.getInt("HLI_LAG_SOFT_PENALTY",15);

        // 병원 + 직선거리(또는 DB에서 계산한 거리)를 조회하는 커스텀 쿼리 호출
        // 반환 rows: Object[]{ Hospital, distance }
        List<Object[]> rows=hospitalRepo.searchWithDistance(lat,lon);
        List<HospitalDtos.HospitalListItem> items=new ArrayList<>();

        for(Object[] r:rows){
            Hospital h=(Hospital)r[0];
            double distance=((Number)r[1]).doubleValue();

            // DB 쿼리 결과가 거리 기준으로 정렬되어 있다고 가정 → 반경을 넘으면 루프 종료(성능 최적화)
            if(distance>radiusKm) break;

            // 병원별 최신 상태(status)를 페이징으로 1건 조회 (가장 최근 상태)
            HospitalStatus latest=statusRepo.findLatestByHpid(h.getHpid(), PageRequest.of(0,1))
                    .stream().findFirst().orElse(null);

            // DB에 이미 계산된 HLI 스냅샷이 있는지 확인하고,
            // 없으면 런타임에서 HliService를 이용해 동적으로 계산한다.
            HliSnapshot snap=null;
            HliService.Computed comp=null;

            if(latest!=null){
                // 상태에 대해 이미 계산된 HLI 스냅샷이 DB에 있으면 사용
                snap=hliRepo.findByStatus_StatusId(latest.getStatusId()).orElse(null);
                // 스냅샷이 없으면 런타임에서 HLI를 동적으로 계산
                if(snap==null) comp=hliService.compute(latest, soft, hard, penalty);
            }

            // HLI 요약 DTO 생성 — 우선순위: DB 스냅샷(snap) > 동적계산(comp) > null(없음)
            HospitalDtos.HliSummary summary=null;
            if(snap!=null){
                // DB에 저장된 스냅샷을 그대로 사용 (신선도/점수 등 포함)
                summary=HospitalDtos.HliSummary.builder()
                        .score(snap.getScore())
                        .level(snap.getLevel().name())
                        .color(colorOf(snap.getLevel()))    // UI용 색상(hex)
                        .lagSec(snap.getLagSec())       // 갱신 지연(초)
                        .staleLevel(snap.getStaleLevel().name())
                        .updatedAt(snap.getCalcAt()) // Instant: 계산(스냅샷) 시각 (UTC)
                        .build();
            }else if(comp!=null){
                // 동적 계산 결과 사용 — 계산 시각이 없으면 현재 UTC 시각을 ms 단위로 잘라서 사용
                Instant updatedAt = (comp.getComputedAt() != null) ? comp.getComputedAt()
                        : Instant.now().truncatedTo(ChronoUnit.MILLIS);

                summary=HospitalDtos.HliSummary.builder()
                        .score(comp.getScore())
                        .level(comp.getLevel().name())
                        .color(colorOf(comp.getLevel()))
                        .lagSec(comp.getLagSec())
                        .staleLevel(comp.getStaleLevel().name())
                        .updatedAt(updatedAt) // Instant
                        .build();
            }

            // 최종 응답용 DTO 항목 생성(병원정보 + 거리 + HLI 요약을 포함한 리스트 항목)
            items.add(HospitalDtos.HospitalListItem.builder()
                    .hpid(h.getHpid()).name(h.getName()).lat(h.getLat()).lon(h.getLon())
                    .addrRoad(h.getAddrRoad()).telEmergency(h.getTelEmergency())
                    .distanceKm(distance).hli(summary).build());
        }
        return new HospitalDtos.NearbyResponse(items);
    }

    /**
     * HLI 레벨을 UI 색상(HEX)으로 매핑
     * HliLevel enum을 UI 표시용 색상(HEX 코드)으로 매핑.
     */
    private String colorOf(HliLevel lvl){
        return switch (lvl){
            case GREEN->"#16a34a";   // 녹색(원활)
            case YELLOW->"#eab308";  // 노란색(혼잡)
            case RED->"#ef4444";     // 빨간색(매우 혼잡)
            case GRAY->"#9ca3af";    // 회색(데이터 지연/무시)
        };
    }
}
