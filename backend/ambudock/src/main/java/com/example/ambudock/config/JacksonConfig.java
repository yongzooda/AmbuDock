package com.example.ambudock.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build()
                // JSON 직렬화 시 null 필드를 제외하여 응답을 깔끔하게 만듦
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}