package com.example.ambudock.domain.common.enums;

/**
 * 행정구역 단위의 매칭 수준을 정의하는 열거형.
 *
 * - AmbuDock에서 재난문자(DisasterAlert)와 병원(Region)을 연결할 때 사용됨.
 * - 예: "서울특별시 강남구 역삼동 침수 주의보" →
 *   병원이 어느 행정단위까지 일치하는지를 구분.
 */

public enum MatchLevel {
    SIDO, // 시도 단위 (예: 서울특별시, 경기도)
    SIGUNGU,// 시군구 단위 (예: 강남구, 수원시)
    DONG // 읍면동 단위 (예: 역삼동, 매탄1동)
}