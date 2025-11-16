package com.example.ambudock.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        // JsonMapper.builder().findAndAddModules()는 classpath에 있는 Jackson 모듈들(e.g. JavaTimeModule 등)을 자동으로 찾아 등록
        ObjectMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .build();

        // 날짜를 숫자(timestamp)로 쓰는 대신 ISO-8601 문자열("2025-11-07T...Z")로 직렬화하게 함.
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // JSON 출력 시 null인 필드는 아예 제외하도록 설정 (payload를 더 깔끔하게).
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 전역(기본) 직렬화/역직렬화 타임존을 UTC로 고정.
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        return mapper;
    }

    @Bean
    public XmlMapper xmlMapper() {
        XmlMapper xmlMapper = XmlMapper.builder()
                .findAndAddModules()
                .build();

        // JSON과 설정을 최대한 맞춰줌
        xmlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        xmlMapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        return xmlMapper;
    }
}
