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
 *- NMC A2 수집 과정에서 처음 본 HPID(병원)가 등장하면,
 * 카카오 로컬 API를 이용해 좌표/주소/행정구역을 획득하고
 * Region(행정구역)과 Hospital(병원 마스터)을 upsert 한다.
 *
 * <절차>
 * 카카오 "키워드 검색"으로 병원 좌표/주소 확보
 * 카카오 "역지오코딩"으로 행정구역 코드/이름 확보
 * Region upsert(코드 기준) → Hospital upsert(+FK region_id)
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
            // 0) 인자 검증: hpid/dutyname 둘 중 하나라도 비면 온보딩 불가
            if (hpid == null || hpid.isBlank()) return false;
            if (dutyname == null || dutyname.isBlank()) {
                log.warn("[Onboard] dutyname is blank for hpid={}", hpid);
                return false;
            }

            // 1) 카카오 키워드 검색(병원명) → 가장 상위 결과 1건 채택
            List<KakaoLocalClient.Place> places = kakao.searchByKeyword(dutyname);
            if (places == null || places.isEmpty()) {
                log.warn("[Onboard] Kakao search empty for dutyname={}", dutyname);
                return false;
            }
            KakaoLocalClient.Place p = places.get(0);

            // Kakao 응답: x=경도, y=위도 (문자열) → 안전 파싱
            double lat = parseD(p.getY());
            double lon = parseD(p.getX());
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                log.warn("[Onboard] invalid lat/lon for dutyname={}", dutyname);
                return false;
            }

            // 2) 역지오코딩 → 행정구역 코드/명 (법정동만 허용)
            KakaoLocalClient.Region rg = kakao.reverseRegion(lat, lon);
            if (!isLegalDong(rg)) {
                log.warn("[Onboard] reverse region (LEGAL/B) not found for {} ({},{}) type={} code={}",
                        dutyname, lat, lon, (rg == null ? null : rg.getRegionType()), (rg == null ? null : rg.getCode()));
                return false; // 법정동 코드가 아니면 저장하지 않음
            }

            // 3) Region upsert (code unique)
            //    - 동일 code 존재 시 갱신(이름 변경/개편 대응)
            Region region = regionRepo.findByCode(rg.getCode()).orElseGet(Region::new);
            region.setCode(rg.getCode());
            // 아래 필드는 실제 Region 엔티티 스키마에 맞춰 매핑
            region.setNameFull(rg.getAddressName());          // 전체 주소 문자열
            region.setNameSido(rg.getRegion1depthName());     // 시/도
            region.setNameSigungu(rg.getRegion2depthName());  // 시/군/구
            region.setNameDong(rg.getRegion3depthName());     // 읍/면/동
            region = regionRepo.save(region);

            // 4) Hospital upsert (+ FK region_id)
            //    - 최초면 새 엔티티, 이미 있으면 주소/전화/좌표 보정
            Hospital ex = hospitalRepo.findById(hpid).orElse(null);
            Hospital h = (ex != null) ? ex : new Hospital();
            h.setHpid(hpid);
            h.setName(dutyname);
            if (dutytel3 != null && !dutytel3.isBlank()) {
                h.setTelEmergency(dutytel3);
            }

            // 좌표/주소(도로명/지번)는 카카오 키워드 검색 결과를 우선 채택
            h.setLat(BigDecimal.valueOf(lat));
            h.setLon(BigDecimal.valueOf(lon));
            // 카카오 키워드 응답 주소 매핑
            h.setAddrRoad(p.getRoadAddressName()); // 도로명 주소
            h.setAddrJibun(p.getAddressName());    // 지번 주소
            h.setRegion(region);                // FK 설정

            // 생성/수정 시각(UTC) 보정: 엔티티의 @PrePersist/@PreUpdate와 이중 보호
            if (h.getCreatedAt() == null) {
                h.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
            }
            h.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
            hospitalRepo.save(h);   // upsert 완료

            return true;
        } catch (Exception e) {
            // 어떤 단계에서든 예외 발생 시 false 반환 (상위 수집 로직이 스킵하도록)
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

    /** Kakao 역지오코딩 결과가 '법정동(region_type=B) + 10자리 코드'인지 검사 */
    private boolean isLegalDong(KakaoLocalClient.Region rg) {
        if (rg == null) return false;
        if (!"B".equals(rg.getRegionType())) return false;  // 법정동만 허용
        String code = rg.getCode();
        if (code == null || code.length() != 10) return false;
        for (int i = 0; i < 10; i++) {
            char c = code.charAt(i);
            if (c < '0' || c > '9') return false;           // 숫자 10자리 보장
        }
        return true;
    }
}
