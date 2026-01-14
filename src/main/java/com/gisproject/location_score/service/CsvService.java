package com.gisproject.location_score.service;

import com.gisproject.location_score.entity.Shop;
import com.gisproject.location_score.repository.ShopRepository;
import com.gisproject.location_score.util.CoordinateTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvService {

    private final ShopRepository shopRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * [OOM 방지 최적화]
     */
    @Transactional
    public int parseAndSave(MultipartFile file, Map<String, String> columnMapping, String encoding, int srid) {
        // 인코딩 설정 (기본값 EUC-KR)
        Charset charset = Charset.forName(encoding != null ? encoding : "EUC-KR");

        // CSV 파싱을 위한 헤더 인덱스 맵
        Map<String, Integer> headerIndexMap = new HashMap<>();
        List<Shop> batchList = new ArrayList<>();
        int savedCount = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), charset))) {

            // 1. 헤더 읽기
            String headerLine = br.readLine();
            if (headerLine == null) return 0;

            // BOM 제거 (UTF-8일 경우)
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            // 헤더 파싱 및 인덱스 매핑
            String[] headers = headerLine.split(","); // 필요시 복잡한 split 로직 적용
            for (int i = 0; i < headers.length; i++) {
                // 따옴표 제거 및 공백 제거
                String cleanHeader = headers[i].replaceAll("^\"|\"$", "").trim();
                headerIndexMap.put(cleanHeader, i);
            }

            // 2. 사용자 매핑 정보를 바탕으로 실제 CSV 인덱스 찾기
            int idxName = findIndex(headerIndexMap, columnMapping.get("shopName"));
            int idxMain = findIndex(headerIndexMap, columnMapping.get("categoryMain"));
            int idxSub = findIndex(headerIndexMap, columnMapping.get("categorySub"));
            int idxAddr = findIndex(headerIndexMap, columnMapping.get("address"));
            int idxLon = findIndex(headerIndexMap, columnMapping.get("lon"));
            int idxLat = findIndex(headerIndexMap, columnMapping.get("lat"));

            // 필수값(좌표) 없으면 중단
            if (idxLon == -1 || idxLat == -1) {
                throw new RuntimeException("CSV 파일에 필수 좌표 컬럼이 없습니다.");
            }

            // 3. 데이터 한 줄씩 읽기 (Streaming)
            String line;
            while ((line = br.readLine()) != null) {
                // 콤마로 분리 (단순 분리)
                // 데이터 안에 콤마가 포함된 복잡한 CSV라면 정규식이나 커스텀 로직 필요: line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                String[] row = line.split(",");

                // 데이터 길이 체크
                if (row.length <= Math.max(idxLat, idxLon)) continue;

                try {
                    // 데이터 추출 및 정제
                    String latStr = getCellValue(row, idxLat);
                    String lonStr = getCellValue(row, idxLon);

                    if (latStr.isEmpty() || lonStr.isEmpty()) continue;

                    double lat = Double.parseDouble(latStr);
                    double lon = Double.parseDouble(lonStr);

                    // 좌표 변환 (5179 -> 4326)
                    if (srid == 5179) {
                        double[] transformed = CoordinateTransformer.transform(srid, lon, lat);
                        lon = transformed[0];
                        lat = transformed[1];
                    }

                    // Entity 생성
                    Shop shop = new Shop();
                    shop.setShopName(getCellValue(row, idxName));
                    shop.setCategoryMain(getCellValue(row, idxMain));
                    shop.setCategorySub(getCellValue(row, idxSub));
                    shop.setAddress(getCellValue(row, idxAddr));
                    shop.setLat(lat);
                    shop.setLon(lon);
                    shop.setGeom(geometryFactory.createPoint(new Coordinate(lon, lat)));

                    batchList.add(shop);

                } catch (Exception e) {
                    // 파싱 에러난 행은 스킵
                    continue;
                }

                // 4. 배치 저장 (1000개씩 끊어서 DB로 보냄)
                if (batchList.size() >= 1000) {
                    saveBatch(batchList);
                    savedCount += batchList.size();
                    batchList.clear(); // 메모리 비우기
                }
            }

            // 남은 데이터 저장
            if (!batchList.isEmpty()) {
                saveBatch(batchList);
                savedCount += batchList.size();
            }

        } catch (Exception e) {
            log.error("CSV 처리 중 오류 발생", e);
            throw new RuntimeException("CSV 업로드 실패: " + e.getMessage());
        }

        return savedCount;
    }

    // 배치 저장 및 영속성 컨텍스트 초기화 (OOM 방지)
    private void saveBatch(List<Shop> shops) {
        shopRepository.saveAll(shops);
        entityManager.flush();
        entityManager.clear();
    }

    // 인덱스 찾기 헬퍼
    private int findIndex(Map<String, Integer> headerMap, String key) {
        if (key == null) return -1;
        return headerMap.getOrDefault(key, -1);
    }

    // 셀 값 가져오기 헬퍼
    private String getCellValue(String[] row, int index) {
        if (index == -1 || index >= row.length) return null;
        return row[index].replaceAll("^\"|\"$", "").trim();
    }
}