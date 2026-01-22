package com.gisproject.location_score.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisproject.location_score.repository.ShopRepository;
import com.gisproject.location_score.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final ShopService shopService;
    private final ShopRepository shopRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * [ETL] 대용량 CSV 업로드 (비동기 처리 + OOM 방지)
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "srid", defaultValue = "4326") int srid
    ) {
        try {
            // 1. 고유 Job ID 생성 (작업 추적용)
            String jobId = UUID.randomUUID().toString();

            // 2. 임시 파일 생성 (비동기 처리를 위해 물리 파일로 저장)
            File tempFile = File.createTempFile("upload_" + jobId, ".csv");
            file.transferTo(tempFile);

            // 3. 비동기 서비스 호출
            // ShopService.uploadLargeCsv에서 자동으로 인코딩 감지 & 좌표 변환 & 배치 저장 수행
            shopService.uploadLargeCsv(tempFile, jobId, srid);

            // 4. Job ID 반환
            Map<String, String> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("message", "대용량 업로드가 시작되었습니다. (Job ID: " + jobId + ")");

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("파일 업로드 실패", e);
            return ResponseEntity.internalServerError().body("파일 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * [Status] 업로드 진행률 조회 (프론트엔드 폴링용)
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getUploadStatus(@PathVariable("jobId") String jobId) {
        String status = ShopService.JOB_STATUS.getOrDefault(jobId, "대기 중...");

        Map<String, String> result = new HashMap<>();
        result.put("jobId", jobId);
        result.put("status", status);

        return ResponseEntity.ok(result);
    }

    /**
     * [Dashboard] 데이터 요약 통계 조회
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getSummary() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", shopRepository.count());

        // 카테고리별 상위 5개 데이터 비중
        String categoryQuery = "SELECT category_main, COUNT(*) as cnt FROM shop_data GROUP BY category_main ORDER BY cnt DESC LIMIT 5";
        List<Map<String, Object>> categoryStats = jdbcTemplate.queryForList(categoryQuery);
        stats.put("categoryStats", categoryStats);

        return ResponseEntity.ok(stats);
    }

    /**
     * [Maintenance] GIS 인덱스 최적화 (VACUUM & REINDEX)
     */
    @PostMapping("/maintenance/optimize")
    public ResponseEntity<String> optimizeDatabase() {
        try {
            // 1. 데드 튜플 정리 및 통계 갱신 (트랜잭션 없이 실행)
            jdbcTemplate.execute("VACUUM ANALYZE shop_data");

            // 2. 인덱스 재구축 (서비스 중단 방지를 위해 CONCURRENTLY)
            jdbcTemplate.execute("REINDEX INDEX CONCURRENTLY idx_shop_geom");


            return ResponseEntity.ok("최적화 완료: VACUUM 및 인덱스 재구축 성공");

        } catch (DataAccessException e) {
            // SQL 에러 상세 로그
            log.error("GIS 최적화 실패", e);
            return ResponseEntity.internalServerError()
                    .body("최적화 실패 (로그 확인 필요): " + e.getMostSpecificCause().getMessage());
        }
    }

    /**
     * [Danger Zone] 전체 데이터 초기화
     */
    @DeleteMapping("/data/clear")
    @Transactional
    public ResponseEntity<String> clearData() {
        shopRepository.deleteAllInBatch();
        return ResponseEntity.ok("전체 상권 데이터가 안전하게 초기화되었습니다.");
    }
}