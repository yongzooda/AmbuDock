package com.example.ambudock.domain.common.enums;


/**
 * 재난문자(DisasterAlert)의 심각도(Severity) 등급을 정의하는 열거형.
 *
 * - AmbuDock에서는 MOIS(행정안전부) 재난문자 내용 분석 결과에 따라
 *   병원 후보를 필터링하거나 경고 배지를 표시할 때 사용됨.
 */

public enum Severity {
    IGNORE,  // 무시 수준 — 단순 정보/주의보 등 영향 없음
    WARN, // 주의 수준 — 이동 차질 가능 (예: 폭우, 강풍, 폭염 등)
    BLOCK // 차단 수준 — 접근 불가/위험 (예: 침수, 대피, 도로통제 등)
}
