package com.example.ambudock.domain.common.enums;

/**
 * 데이터 신선도(Freshness)를 등급화한 열거형.
 *
 * - AmbuDock에서는 병원 가용병상 정보(NMC API 갱신 시각 기준)가
 *   얼마나 최신인지 판단하여 HLI 점수 신뢰도에 반영함.
 */

public enum StaleLevel {
    FRESH, // 최신 데이터 (5분 이내 갱신) → 점수 100% 반영
    SOFT, // 다소 지연된 데이터 (5~60분 지연) → 감점 적용
    HARD  // 오래된 데이터 (예: 60분 이상 지연) → 무효 처리 / GRAY 표시
}
