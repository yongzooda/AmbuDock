package com.example.ambudock.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * SecurityConfig (Spring Security 6.x)
 * - /api/admin/** 는 ROLE_ADMIN 필요
 * - 기본 인증 방식: HTTP Basic (개발/테스트용)
 * - 메서드 보안 활성화(@PreAuthorize 사용 가능)
 */
@Configuration
@EnableMethodSecurity // @PreAuthorize 활성화
public class SecurityConfig {

    @Value("${ambudock.admin.username}")
    private String adminUsername;

    @Value("${ambudock.admin.password}")
    private String adminPassword;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // REST API 전용: CSRF 기본 비활성화 (브라우저 폼이 있다면 재검토)
                .csrf(csrf -> csrf.disable())

                // 권한 규칙
                .authorizeHttpRequests(auth -> auth
                        // admin API 보호
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 문서/헬스/핑 등 공개 (필요하면 조정)
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/api/tmap/ping",
                                "/api/recommend/ping"
                        ).permitAll()

                        // 그 외 API는 허용 (프로덕션에서는 더 제한 권장)
                        .anyRequest().permitAll()
                )

                // deprecated 대신 Customizer 사용 (6.1 이후 권장)
                .httpBasic(withDefaults());

        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(PasswordEncoder passwordEncoder) {
        var admin = User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
