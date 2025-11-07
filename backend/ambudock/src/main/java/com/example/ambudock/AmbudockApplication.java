package com.example.ambudock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling   // @Scheduled 활성화 (A2/A3/A4)
@EnableCaching      // @Cacheable / @CacheEvict 등 활성화
public class AmbudockApplication {

    public static void main(String[] args) {
        // 앱 전체를 UTC 타임존으로 강제 고정 (권장)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        SpringApplication.run(AmbudockApplication.class, args);
    }

}
