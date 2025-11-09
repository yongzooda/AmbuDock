package com.example.ambudock.service;

import com.example.ambudock.domain.hospital.Hospital;
import com.example.ambudock.domain.region.Region;
import com.example.ambudock.repository.HospitalRepository;
import com.example.ambudock.repository.RegionRepository;
import com.example.ambudock.scheduler.client.KakaoLocalClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * HospitalOnboardingService
 *
 * 사진의 ②→③→④ 절차를 자동화:
 *  - (②) 카카오 키워드 검색: 병원명(dutyname)으로 좌표/주소 확보
 *  - (③) 카카오 역지오코딩: lat/lon으로 행정구역 코드/명 확보
 *  - (④) Region upsert → Hospital upsert(+FK region_id)
 *
 * NMC A2 수집 중 hpid가 미등록일 때 호출하여 병원 마스터와 region을 선행 생성/보정한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HospitalOnboardingService {

    private final HospitalRepository hospitalRepo;
    private final RegionRepository regionRepo;
    private final KakaoLocalClient kakao;

    /**
     * 병원/지역 온보딩을 보장한다. (있으면 보정, 없으면 삽입)
     *
     * @param hpid      NMC 고유 병원 ID
     * @param dutyname  병원명(키워드 검색용)
     * @param dutytel3  응급실 전화(있으면 메타 보정)
     * @return 성공 시 true (Region/Hospital이 유효하게 존재), 실패 시 false
     */
    @Transactional
    public boolean ensureHospitalAndRegion(String hpid, String dutyname, String dutytel3) {
        try {
            // 0) 인자 검증
            if (hpid == null || hpid.isBlank()) return false;
            if (dutyname == null || dutyname.isBlank()) {
                log.warn("[Onboard] dutyname is blank for hpid={}", hpid);
                return false;
            }

            // 1) 카카오 키워드 검색 (병원명 기준, 최상위 1건 사용)
            List<KakaoLocalClient.Place> places = kakao.searchByKeyword(dutyname);
            if (places == null || places.isEmpty()) {
                log.warn("[Onboard] Kakao search empty for dutyname={}", dutyname);
                return false;
            }
            KakaoLocalClient.Place p = places.get(0);

            double lat = parseD(p.getY()); // Kakao: y=위도, x=경도(문자열)
            double lon = parseD(p.getX());
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                log.warn("[Onboard] invalid lat/lon for dutyname={}", dutyname);
                return false;
            }

            // 2) 역지오코딩 → 행정구역 코드/명
            KakaoLocalClient.Region rg = kakao.reverseRegion(lat, lon);
            if (rg == null || rg.getCode() == null || rg.getCode().isBlank()) {
                log.warn("[Onboard] reverse region failed for {} ({},{})", dutyname, lat, lon);
                return false;
            }

            // 3) Region upsert (code unique)
            Region region = regionRepo.findByCode(rg.getCode()).orElseGet(Region::new);
            region.setCode(rg.getCode());
            // Region 엔티티 필드에 맞춤 매핑
            region.setNameFull(rg.getAddressName());          // 전체 주소 문자열
            region.setNameSido(rg.getRegion1depthName());     // 시/도
            region.setNameSigungu(rg.getRegion2depthName());  // 시/군/구
            region.setNameDong(rg.getRegion3depthName());     // 읍/면/동
            region = regionRepo.save(region);

            // 4) Hospital upsert (+ FK region_id)
            Hospital ex = hospitalRepo.findById(hpid).orElse(null);
            Hospital h = (ex != null) ? ex : new Hospital();
            h.setHpid(hpid);
            h.setName(dutyname);
            if (dutytel3 != null && !dutytel3.isBlank()) {
                h.setTelEmergency(dutytel3);
            }
            h.setLat(BigDecimal.valueOf(lat));
            h.setLon(BigDecimal.valueOf(lon));
            // 카카오 키워드 응답 주소 매핑
            h.setAddrRoad(p.getRoadAddressName()); // 도로명 주소
            h.setAddrJibun(p.getAddressName());    // 지번 주소
            h.setRegion(region);
            if (h.getCreatedAt() == null) {
                h.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
            }
            h.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
            hospitalRepo.save(h);

            return true;
        } catch (Exception e) {
            log.warn("[Onboard] failed for hpid={}, cause={}", hpid, e.toString());
            return false;
        }
    }

    // Kakao x/y(String) → double 안전 파서
    private double parseD(Object v) {
        if (v == null) return Double.NaN;
        try {
            if (v instanceof Number n) return n.doubleValue();
            return Double.parseDouble(v.toString());
        } catch (Exception ignore) {
            return Double.NaN;
        }
    }
}
