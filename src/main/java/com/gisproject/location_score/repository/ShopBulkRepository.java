package com.gisproject.location_score.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ShopBulkRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void saveAllBatch(List<Object[]> args) {
        // PostGIS 4326 좌표계 변환 포함
        String sql = """
            INSERT INTO shop_data 
            (shop_name, category_main, category_sub, address, lat, lon, geom) 
            VALUES (?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))
        """;

        // 배열(Object[])을 사용하여 메모리 절약
        jdbcTemplate.batchUpdate(sql, args, args.size(),
                (PreparedStatement ps, Object[] arg) -> {
                    ps.setString(1, (String) arg[0]); // name
                    ps.setString(2, (String) arg[1]); // main
                    ps.setString(3, (String) arg[2]); // sub
                    ps.setString(4, (String) arg[3]); // address

                    // lat, lon (Double)
                    ps.setObject(5, arg[4]);
                    ps.setObject(6, arg[5]);

                    // geom (lon, lat 순서 주의)
                    ps.setObject(7, arg[5]);
                    ps.setObject(8, arg[4]);
                });
    }
}
