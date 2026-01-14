package com.gisproject.location_score.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnalysisResponse {
    private double totalScore;   // 종합 점수 (ex: 8.5)
    private String grade;        // 등급 (ex: 다이아몬드, 골드)

    private CategoryGroup transport; // 교통
    private CategoryGroup medical;   // 의료
    private CategoryGroup mart;      // 편의/마트
    private CategoryGroup food;      // 음식점
    private CategoryGroup cafe;      // 카페

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CategoryGroup {
        private int count; // 가게 개수
        private int score; // 해당 항목 점수 (차트용)
        @Builder.Default
        private List<ShopItem> items = new ArrayList<>();
    }

    // 내부 클래스: 지도에 찍을 개별 가게 정보
    @Data
    @AllArgsConstructor
    public static class ShopItem {
        private Long id;
        private String shopName;
        private String categoryMain;
        private String categorySub;
        private double lat;
        private double lon;
        private int dist; // 중심점으로부터의 거리(m)
    }
}