package com.example.ambudock.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Instant <-> Timestamp 변환기
 * - JPA가 엔티티 필드(Instant)를 DB 저장 시 java.sql.Timestamp로 변환하고,
 *   DB에서 읽을 때 다시 Instant로 변환하도록 도와줌.
 */
@Converter(autoApply = true)
public class InstantAttributeConverter implements AttributeConverter<Instant, Timestamp> {

    /**
     * 엔티티(Instant) -> DB 컬럼(Timestamp)
     * - null 안전 처리
     * - Instant는 UTC 기준의 타임스탬프(타임존 정보가 아닌 절대 시점)
     */
    @Override
    public Timestamp convertToDatabaseColumn(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    /**
     * DB 컬럼(Timestamp) -> 엔티티(Instant)
     * - null 안전 처리
     * - sql.Timestamp.toInstant()로 Instant 복원
     */
    @Override
    public Instant convertToEntityAttribute(Timestamp sqlTimestamp) {
        return sqlTimestamp == null ? null : sqlTimestamp.toInstant();
    }
}
