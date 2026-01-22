package com.gisproject.location_score.util;
import com.gisproject.location_score.entity.Shop;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.function.Predicate;

@Getter
@RequiredArgsConstructor
public enum ShopCategory {

    // 1. 교통
    TRANSPORT("교통", shop -> {
        String main = safe(shop.getCategoryMain());
        String sub = safe(shop.getCategorySub());
        return main.contains("교통") || sub.contains("지하철") || sub.contains("철도") || sub.contains("역") || sub.contains("여객");
    }),

    // 2. 의료
    MEDICAL("의료", shop -> {
        String main = safe(shop.getCategoryMain());
        String sub = safe(shop.getCategorySub());
        String name = safe(shop.getShopName());

        // Q: 보건업 코드, S2: 수리/개인서비스(중 일부 뷰티/마사지가 섞일 수 있음 -> 키워드로 방어)
        boolean isMedicalKeyword = (main.contains("의료") || sub.contains("병원") || sub.contains("약국") || sub.contains("의원") ||
                name.contains("의원") || name.contains("병원") || name.contains("약국") || name.contains("피부과"));

        return isMedicalKeyword && !sub.contains("동물") && !sub.contains("수의");
    }),

    // 3. 카페
    CAFE("카페", shop -> {
        String main = safe(shop.getCategoryMain());
        String sub = safe(shop.getCategorySub());
        String name = safe(shop.getShopName());

        // 대분류가 명확히 카페인 경우
        if (main.equals("카페") || main.contains("카페")) return true;

        // 소분류 코드(I212) 및 키워드 검사
        return sub.contains("카페") || sub.contains("커피") || sub.contains("찻집") ||
                sub.contains("비알콜") || sub.contains("휴게") ||
                sub.startsWith("I212") || // [중요] 비알콜 음료점업 코드
                name.contains("스타벅스") || name.contains("이디야") || name.contains("투썸") ||
                name.contains("메가커피") || name.contains("컴포즈") || name.contains("폴바셋") ||
                name.contains("카페") || name.contains("커피");
    }),

    // 4. 음식점 (카페가 아닌 나머지 I2 코드 및 음식 키워드)
    FOOD("음식점", shop -> {
        String main = safe(shop.getCategoryMain());
        String sub = safe(shop.getCategorySub());

        // I2: 음식점업 코드
        return main.contains("음식") || main.equals("I2") || sub.startsWith("I2");
    }),

    // 5. 편의 (나머지 전부)
    // 교통, 의료, 카페, 음식점이 아니면 -> 무조건 '편의'.
    // (편의점, 마트, 미용실, 학원, 세탁소 등등 모두 포함됨)
    CONVENIENCE("편의", shop -> true),

    // 6. 기타 (도달 불가능한 코드 - 안전장치)
    ETC("기타", shop -> false);


    private final String description;
    private final Predicate<Shop> matcher;

    public static ShopCategory classify(Shop shop) {
        return Arrays.stream(values())
                .filter(category -> category.matcher.test(shop)) // 순서대로
                .findFirst()
                .orElse(ETC);
    }

    private static String safe(String input) {
        return input == null ? "" : input.toUpperCase();
    }
}