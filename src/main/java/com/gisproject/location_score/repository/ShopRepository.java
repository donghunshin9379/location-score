package com.gisproject.location_score.repository;

import com.gisproject.location_score.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying; // 필수
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    /**
     * 반경 내 검색 (GIST 인덱스 활용)
     * ST_DWithin = GIST 인덱스 함수
     * distance 단위: 4326(도, degree) / 5179(미터, meter) -> geography 타입 캐스팅으로 미터 단위 사용
     */
    @Query(value = """
    SELECT * FROM shop_data s 
    WHERE ST_DWithin(
        s.geom::geography, 
        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, 
        :distance
    )
    """, nativeQuery = true)
    List<Shop> findNearbyShops(@Param("lon") double lon, @Param("lat") double lat, @Param("distance") double distance);


    /**
     * Leaflet 연동용 GeoJSON 직접 조회
     */
    @Query(value = """
    SELECT jsonb_build_object(
        'type', 'FeatureCollection',
        'features', jsonb_agg(
            jsonb_build_object(
                'type', 'Feature',
                'geometry', ST_AsGeoJSON(geom)::jsonb,
                'properties', jsonb_build_object('name', shop_name, 'id', id)
            )
        )
    )
    FROM shop_data
    WHERE geom && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
    """, nativeQuery = true)
    String findShopsAsGeoJson(@Param("minLon") double minLon, @Param("minLat") double minLat, @Param("maxLon") double maxLon, @Param("maxLat") double maxLat);


    @Query(value = """
    SELECT * FROM shop_data 
    WHERE category_main = :category 
      AND ST_DWithin(geom::geography, ST_MakePoint(:lon, :lat), :distance)
    """, nativeQuery = true)
    List<Shop> findByCategoryAndLocation(@Param("category") String category, @Param("lon") double lon, @Param("lat") double lat, @Param("distance") double distance);


    /**
     * 중복 제거 쿼리 (업로드 서비스에서 호출)
     */
    @Modifying
    @Query(value = """
        DELETE FROM shop_data a
        USING shop_data b
        WHERE a.id > b.id             
          AND a.shop_name = b.shop_name 
          AND a.address = b.address
    """, nativeQuery = true)
    void removeDuplicateShops();
}