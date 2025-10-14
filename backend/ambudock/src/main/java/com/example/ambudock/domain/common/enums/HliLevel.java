package com.example.ambudock.domain.common.enums;

/**
 * HLI (Hospital Load Index, 응급실 혼잡지수)의 시각적 상태 구분 열거형.
 *
 * - 각 값은 병원의 현재 수용 가능 상태를 색상 코드로 표현함.
 * - AmbuDock 전반에서 HLI 점수를 색상 기반으로 표시할 때 사용됨.
 */
public enum HliLevel {
    GREEN, // 정상 (여유 있음)
    YELLOW, // 혼잡 (주의 필요)
    RED, // 매우 혼잡 (수용 한계에 근접)
    GRAY  // 데이터 지연/무효 (갱신 지연 등으로 신뢰 불가)
}