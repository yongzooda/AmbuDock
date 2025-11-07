package com.example.ambudock.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        // JsonMapper.builder().findAndAddModules()는 classpath에 있는 모듈을 자동으로 등록합니다.
        ObjectMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .build();

        // 안전을 위해 JavaTimeModule을 명시적으로 한 번 더 등록 (중복 등록해도 안전)
        mapper.registerModule(new JavaTimeModule());

        // 날짜를 숫자타입(timestamp)으로 쓰지 않고 ISO-8601 문자열로 직렬화
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // null 필드 제외
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 전역 직렬화/역직렬화 타임존을 UTC로 고정
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        return mapper;
    }
}
