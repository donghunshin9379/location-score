package com.gisproject.location_score.repository;

import com.gisproject.location_score.entity.Shop;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ShopBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 대용량 데이터 배치 삽입 (속도 최적화)
     * Shop 엔티티 리스트를 받아 한 번에 DB에 꽂아 넣습니다.
     */
    @Transactional
    public void batchInsertShops(List<Shop> shops) {
        String sql = """
            INSERT INTO shop_data 
            (shop_name, category_main, category_sub, address, lat, lon, geom) 
            VALUES (?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))
        """;

        // JdbcTemplate의 batchUpdate는 내부적으로 JDBC Batch 처리를 수행합니다.
        // 1000개 단위로 쪼개는 로직은 Service 레벨에서 관리하는 것이 트랜잭션 관리에 유리하지만,
        // Repository에서 리스트 전체를 받아 처리해도 JdbcTemplate이 알아서 처리합니다.

        jdbcTemplate.batchUpdate(sql, shops, 1000, (PreparedStatement ps, Shop shop) -> {
            ps.setString(1, shop.getShopName());
            ps.setString(2, shop.getCategoryMain());
            ps.setString(3, shop.getCategorySub());
            ps.setString(4, shop.getAddress());

            // Null Safe 처리
            Double lat = shop.getLat();
            Double lon = shop.getLon();

            if (lat != null) ps.setDouble(5, lat);
            else ps.setObject(5, null);

            if (lon != null) ps.setDouble(6, lon);
            else ps.setObject(6, null);

            // PostGIS Geometry (lon, lat)
            if (lon != null && lat != null) {
                ps.setDouble(7, lon); // X
                ps.setDouble(8, lat); // Y
            } else {
                ps.setObject(7, null);
                ps.setObject(8, null);
            }
        });
    }
}