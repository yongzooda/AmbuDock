package com.example.ambudock.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    // TMAP 프록시 기본 URL (없으면 localhost:8080 사용)
    @Value("${tmap.proxy.base-url}")
    private String tmapBaseUrl;

    // 요청 타임아웃(ms) 기본값 1500ms
    @Value("${tmap.proxy.timeout-ms}")
    private int timeoutMs;

    @Bean(name = "tmapWebClient")
    public WebClient tmapWebClient() {

        // HttpClient 설정: 연결/응답/읽기/쓰기 타임아웃 지정
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs) // 연결 시도 제한
                .responseTimeout(Duration.ofMillis(timeoutMs))   // 응답 대기 제한
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)) // 읽기 제한
                        .addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))) // 쓰기 제한
                .compress(true); // 응답 압축 허용 (gzip 등)

        return WebClient.builder()
                .baseUrl(tmapBaseUrl) // 기본 요청 URL
                .clientConnector(new ReactorClientHttpConnector(httpClient)) // Netty 기반 연결
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 최대 응답 버퍼 2MB
                        .build())
                .defaultHeader("Accept", "application/json") // 기본 헤더 설정
                .build();
    }
}
