package com.example.ambudock.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PageResponse<T> {

    /** 조회된 데이터 항목 리스트 */
    private List<T> items; //실제 데이터 목록아 들어감

    /** 전체 데이터 개수 (페이지네이션 계산용) */
    private long total; //총 레코드 수
}
