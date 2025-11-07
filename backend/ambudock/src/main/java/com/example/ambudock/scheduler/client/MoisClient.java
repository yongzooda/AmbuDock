package com.example.ambudock.scheduler.client;

import com.example.ambudock.dto.MoisAlertDto;
import com.example.ambudock.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * MOIS(행안부 재난문자) 수집 클라이언트 (인터페이스 분리 없이 단일 클래스)
 *
 * AppConfig keys:
 *  - MOIS_API_URL
 *  - MOIS_API_TIMEOUT_MS
 *
 * 실제 엔드포인트("/alerts/latest")는 운영 스펙에 맞춰 수정하세요.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MoisClient {

    private final AppConfigService cfg;

    private static final String KEY_BASE = "MOIS_API_URL";
    private static final String KEY_TIMEOUT = "MOIS_API_TIMEOUT_MS";

    public List<MoisAlertDto> fetchLatestAlerts() {
        String base = cfg.get(KEY_BASE, "http://localhost:9010");
        int timeoutMs = Math.max(500, cfg.getInt(KEY_TIMEOUT, 2000));
        try {
            WebClient wc = WebClient.builder()
                    .baseUrl(base)
                    .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .build();

            // TODO: 실제 MOIS 엔드포인트로 변경
            Flux<MoisAlertDto> flux = wc.get()
                    .uri("/alerts/latest")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToFlux(MoisAlertDto.class);

            List<MoisAlertDto> list = flux.collectList().block(Duration.ofMillis(timeoutMs));
            return list == null ? Collections.emptyList() : list;
        } catch (Exception ex) {
            log.warn("MOIS fetch failed: base={}, cause={}", base, ex.toString());
            return Collections.emptyList();
        }
    }
}
