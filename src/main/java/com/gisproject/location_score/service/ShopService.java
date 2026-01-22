package com.gisproject.location_score.service;

import com.gisproject.location_score.dto.AnalysisResponse;
import com.gisproject.location_score.entity.Shop;
import com.gisproject.location_score.repository.ShopBatchRepository; // [최적화] 대량 삽입용
import com.gisproject.location_score.repository.ShopRepository;      // [최적화] 조회/공간쿼리용
import com.gisproject.location_score.util.CoordinateTransformer;
import com.gisproject.location_score.util.EncodingDetector;
import com.gisproject.location_score.util.ShopCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final ShopBatchRepository shopBatchRepository;

    // SRID 4326(위경도) 기준 Geometry 생성기
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    // 진행률 상태 저장소
    public static final ConcurrentHashMap<String, String> JOB_STATUS = new ConcurrentHashMap<>();

    /**
     * [기능 1] 대용량 CSV 업로드 (JDBC Batch Update 적용)
     */
    @Async
    public void uploadLargeCsv(File tempFile, String jobId, int sourceSrid) {
        long startTime = System.currentTimeMillis();
        JOB_STATUS.put(jobId, "데이터 분석 및 저장 시작...");

        // 인코딩 자동 감지
        Charset charset = EncodingDetector.detect(tempFile);
        log.info("Job {} - 감지된 인코딩: {}", jobId, charset);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(tempFile), charset))) {

            String headerLine = br.readLine();
            if (headerLine == null) return;

            // 헤더 파싱
            String[] headers = headerLine.replace("\"", "").split(",");
            int nameIdx = findIndex(headers, "상호명", "업소명", "가게명", "shop_name");
            int mainIdx = findIndex(headers, "상권업종대분류명", "대분류", "category_main");
            int subIdx = findIndex(headers, "상권업종소분류명", "소분류", "category_sub");
            int addrIdx = findIndex(headers, "도로명주소", "주소", "address");
            int latIdx = findIndex(headers, "위도", "lat", "latitude", "y");
            int lonIdx = findIndex(headers, "경도", "lon", "longitude", "x");

            if (nameIdx == -1 || latIdx == -1 || lonIdx == -1) {
                JOB_STATUS.put(jobId, "실패: 필수 컬럼(상호명, 위도, 경도) 누락");
                return;
            }

            List<Shop> batchList = new ArrayList<>(1000);
            String line;
            int count = 0;

            while ((line = br.readLine()) != null) {
                try {
                    String[] rowData = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                    if (rowData.length <= Math.max(latIdx, lonIdx)) continue;

                    String latStr = rowData[latIdx].replace("\"", "").trim();
                    String lonStr = rowData[lonIdx].replace("\"", "").trim();
                    if (latStr.isEmpty() || lonStr.isEmpty()) continue;

                    double rawLat = Double.parseDouble(latStr);
                    double rawLon = Double.parseDouble(lonStr);

                    // 좌표 변환 (GRS80 -> WGS84 등)
                    double[] coords = CoordinateTransformer.transform(sourceSrid, rawLon, rawLat);
                    double finalLon = coords[0];
                    double finalLat = coords[1];

                    Shop shop = new Shop();
                    shop.setShopName(getVal(rowData, nameIdx));
                    shop.setCategoryMain(getVal(rowData, mainIdx));
                    shop.setCategorySub(getVal(rowData, subIdx));
                    shop.setAddress(getVal(rowData, addrIdx));
                    shop.setLat(finalLat);
                    shop.setLon(finalLon);
                    // Geometry 생성 (PostGIS 저장용)
                    shop.setGeom(geometryFactory.createPoint(new Coordinate(finalLon, finalLat)));

                    batchList.add(shop);
                    count++;

                    if (batchList.size() >= 1000) {
                        shopBatchRepository.batchInsertShops(batchList);
                        batchList.clear(); // 메모리 즉시 확보

                        if (count % 10000 == 0) {
                            JOB_STATUS.put(jobId, count + "건 저장 중...");
                            log.info("Job {} - {}건 진행", jobId, count);
                        }
                    }

                } catch (Exception e) {
                    continue; // 개별 행 에러는 무시하고 진행
                }
            }

            // 남은 데이터 처리
            if (!batchList.isEmpty()) {
                shopBatchRepository.batchInsertShops(batchList);
            }

            long time = (System.currentTimeMillis() - startTime) / 1000;
            JOB_STATUS.put(jobId, "완료! 총 " + count + "건 저장 (" + time + "초)");

        } catch (Exception e) {
            log.error("업로드 에러", e);
            JOB_STATUS.put(jobId, "에러: " + e.getMessage());
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }

    /**
     * [기능 2] 상권 분석
     */
    @Transactional(readOnly = true)
    public AnalysisResponse analyze(double lat, double lon, double radiusKm) {
        List<Shop> shops = shopRepository.findNearbyShops(lon, lat, radiusKm * 1000);

        List<AnalysisResponse.ShopItem> tList = new ArrayList<>(); // 교통
        List<AnalysisResponse.ShopItem> mList = new ArrayList<>(); // 의료
        List<AnalysisResponse.ShopItem> cList = new ArrayList<>(); // 편의
        List<AnalysisResponse.ShopItem> fList = new ArrayList<>(); // 음식
        List<AnalysisResponse.ShopItem> cafList = new ArrayList<>(); // 카페

        for (Shop s : shops) {
            int dist = (int) calculateDistance(lat, lon, s.getLat(), s.getLon());

            AnalysisResponse.ShopItem item = new AnalysisResponse.ShopItem(
                    s.getId(), s.getShopName(), s.getCategoryMain(), s.getCategorySub(), s.getLat(), s.getLon(), dist
            );

            switch (ShopCategory.classify(s)) {
                case TRANSPORT -> tList.add(item);
                case MEDICAL -> mList.add(item);
                case CONVENIENCE -> cList.add(item);
                case CAFE -> cafList.add(item);
                case FOOD -> fList.add(item);
            }
        }

        // 점수 계산 (각 리스트 사이즈 기반)
        int tScore = calcScore(tList.size(), 3, 20);
        int mScore = calcScore(mList.size(), 50, 15);
        int cScore = calcScore(cList.size(), 30, 15);
        int fScore = calcScore(fList.size(), 400, 30);
        int cafScore = calcScore(cafList.size(), 100, 20);

        double totalRaw = tScore + mScore + cScore + fScore + cafScore;
        double finalScore = Math.min(totalRaw / 10.0, 10.0);

        return AnalysisResponse.builder()
                .totalScore(Double.parseDouble(String.format("%.1f", finalScore)))
                .grade(getGrade(finalScore))
                .transport(new AnalysisResponse.CategoryGroup(tList.size(), tScore, tList))
                .medical(new AnalysisResponse.CategoryGroup(mList.size(), mScore, mList))
                .mart(new AnalysisResponse.CategoryGroup(cList.size(), cScore, cList))
                .food(new AnalysisResponse.CategoryGroup(fList.size(), fScore, fList))
                .cafe(new AnalysisResponse.CategoryGroup(cafList.size(), cafScore, cafList))
                .build();
    }

    // --- Helper Methods ---

    private int calcScore(int current, int target, int maxScore) {
        if (current >= target) return maxScore;
        return (int) Math.round(((double) current / target) * maxScore);
    }

    private void sortListByDist(List<AnalysisResponse.ShopItem> list) {
        list.sort(Comparator.comparingInt(AnalysisResponse.ShopItem::getDist));
    }

    private String getGrade(double score) {
        if (score >= 9.0) return "다이아몬드";
        if (score >= 7.5) return "골드";
        if (score >= 5.0) return "실버";
        return "브론즈";
    }

    // 표시용 거리 계산 (Haversine)
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
        dist = Math.acos(dist);
        dist = Math.toDegrees(dist);
        dist = dist * 60 * 1.1515 * 1609.344; // 미터 단위 변환
        return dist;
    }

    private String getVal(String[] row, int idx) {
        if (idx == -1 || idx >= row.length) return null;
        return row[idx].replace("\"", "").trim();
    }

    private int findIndex(String[] headers, String... keywords) {
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].replace("\"", "").trim();
            for (String kw : keywords) {
                if (h.equalsIgnoreCase(kw) || h.contains(kw)) return i;
            }
        }
        return -1;
    }
}