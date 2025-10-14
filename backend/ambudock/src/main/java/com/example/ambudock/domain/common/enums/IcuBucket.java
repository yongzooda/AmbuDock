package com.example.ambudock.domain.common.enums;

/**
 * ICU(중환자실·수술실 등) 가용 병상 수준을 구간화한 열거형.
 *
 * - AmbuDock의 HLI 계산 로직에서 ER 가용 병상 외에
 *   ICU 가용도를 보정 점수(icu_adjust)로 반영할 때 사용됨.
 * - 각 구간은 hv2~hv6, hvoc 등의 합계를 기준으로 구분.
 */

public enum IcuBucket {
    NONE, // 0개: ICU/수술실 가용 병상 없음 (가장 심각)
    CRITICAL_LOW, // 1개: 매우 부족
    LOW, // 2~4개: 부족
    MID, // 5~9개: 보통
    HIGH  // 10개 이상: 여유 있음
}
