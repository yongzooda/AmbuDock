package com.example.ambudock.scheduler.client;

import com.example.ambudock.dto.NmcStatusDto;
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
 * NMC 실시간 상태 수집 클라이언트 (인터페이스 분리 없이 단일 클래스)
 *
 * AppConfig keys:
 *  - NMC_API_URL (ex: https://nmc.example/api)
 *  - NMC_API_TIMEOUT_MS (ex: 1500)
 *
 * 실제 엔드포인트 경로("/statuses/latest")는 운영 API에 맞춰 수정하세요.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NmcClient {

    private final AppConfigService cfg;

    private static final String KEY_BASE = "NMC_API_URL";
    private static final String KEY_TIMEOUT = "NMC_API_TIMEOUT_MS";

    /**
     * 외부 NMC API에서 최신 상태 목록을 가져온다.
     * 실패 시 빈 리스트 반환(스케줄러가 개별 항목별로 처리/로깅).
     */
    public List<NmcStatusDto> fetchLatestStatuses() {
        String base = cfg.get(KEY_BASE, "http://localhost:9000");
        int timeoutMs = Math.max(500, cfg.getInt(KEY_TIMEOUT, 1500));
        try {
            WebClient wc = WebClient.builder()
                    .baseUrl(base)
                    .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .build();

            // TODO: 운영 API 경로로 변경
            Flux<NmcStatusDto> flux = wc.get()
                    .uri("/statuses/latest")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToFlux(NmcStatusDto.class);

            List<NmcStatusDto> list = flux.collectList().block(Duration.ofMillis(timeoutMs));
            return list == null ? Collections.emptyList() : list;
        } catch (Exception ex) {
            log.warn("NMC fetch failed: base={}, cause={}", base, ex.toString());
            return Collections.emptyList();
        }
    }
}
