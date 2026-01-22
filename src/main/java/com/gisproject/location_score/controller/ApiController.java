package com.gisproject.location_score.controller;

import com.gisproject.location_score.dto.AnalysisResponse;
import com.gisproject.location_score.repository.ShopRepository;
import com.gisproject.location_score.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiController {

    private final ShopService shopService;
    private final ShopRepository shopRepository;

    @GetMapping("/nearby")
    public ResponseEntity<?> analyzeLocation(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "0.5") double km
    ) {
        // [안전장치 1] 반경 제한 (최대 1km) -> 서버 부하 방지
        if (km > 1.0) {
            log.warn("과도한 반경 요청 거부: {}km", km);
            return ResponseEntity.badRequest().body("검색 반경은 최대 1km를 초과할 수 없습니다.");
        }

        log.info("입지 분석 요청 - 위도: {}, 경도: {}, 반경: {}km", lat, lon, km);
        AnalysisResponse result = shopService.analyze(lat, lon, km);
        return ResponseEntity.ok(result);
    }

    /**
     * [서비스] 헥사곤(육각형) 타일 데이터 API
     */
    @GetMapping("/hexagon")
    public ResponseEntity<String> getHexagons(
            @RequestParam double minLon, @RequestParam double minLat,
            @RequestParam double maxLon, @RequestParam double maxLat) {

        // [안전장치 2] 지도 범위 제한
        // 위도/경도 차이가 약 0.05도(약 5km 정도) 이상이면 요청 거부 (성능 보호)
        if (Math.abs(maxLon - minLon) > 0.05 || Math.abs(maxLat - minLat) > 0.05) {
            return ResponseEntity.ok("{\"type\": \"FeatureCollection\", \"features\": []}");
        }

        String geoJson = shopRepository.findShopsAsGeoJson(minLon, minLat, maxLon, maxLat);

        // 데이터가 없으면 빈 GeoJSON 반환
        if (geoJson == null) {
            geoJson = "{\"type\": \"FeatureCollection\", \"features\": []}";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(geoJson);
    }
}