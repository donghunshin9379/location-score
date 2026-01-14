package com.gisproject.location_score.repository;

import com.gisproject.location_score.entity.Shop;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ShopBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void batchInsertShops(List<Shop> shops) {
        //  Geom 생성은 DB 함수(ST_SetSRID, ST_MakePoint)에 위임하여 속도 향상
        String sql = """
            INSERT INTO shop_data 
            (shop_name, category_main, category_sub, address, lat, lon, geom) 
            VALUES (?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))
        """;

        // 2. 배치 사이즈 설정 (1000건씩 커밋)
        int batchSize = 1000;

        for (int i = 0; i < shops.size(); i += batchSize) {
            List<Shop> batchList = shops.subList(i, Math.min(i + batchSize, shops.size()));

            jdbcTemplate.batchUpdate(sql, batchList, batchList.size(),
                    (ps, shop) -> {
                        ps.setString(1, shop.getShopName());
                        ps.setString(2, shop.getCategoryMain());
                        ps.setString(3, shop.getCategorySub());
                        ps.setString(4, shop.getAddress());

                        // lat, lon이 null일 경우 처리
                        if (shop.getLat() != null) ps.setDouble(5, shop.getLat());
                        else ps.setObject(5, null);

                        if (shop.getLon() != null) ps.setDouble(6, shop.getLon());
                        else ps.setObject(6, null);

                        // PostGIS 함수용 파라미터 (lon, lat 순서 주의)
                        if (shop.getLon() != null && shop.getLat() != null) {
                            ps.setDouble(7, shop.getLon()); // X좌표
                            ps.setDouble(8, shop.getLat()); // Y좌표
                        } else {
                            ps.setObject(7, null);
                            ps.setObject(8, null);
                        }
                    });
        }
    }

}