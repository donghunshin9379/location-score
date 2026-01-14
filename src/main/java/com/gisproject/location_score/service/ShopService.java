package com.gisproject.location_score.service;

import com.gisproject.location_score.dto.AnalysisResponse;
import com.gisproject.location_score.entity.Shop;
import com.gisproject.location_score.repository.ShopRepository;
import com.gisproject.location_score.util.CoordinateTransformer; // 유틸 호출
import com.gisproject.location_score.util.EncodingDetector;    // 유틸 호출
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @PersistenceContext
    private EntityManager entityManager;

    // 진행률 상태 저장소
    public static final ConcurrentHashMap<String, String> JOB_STATUS = new ConcurrentHashMap<>();

    /**
     * [최종] 대용량 CSV 업로드
     */
    @Async
    @Transactional
    public void uploadLargeCsv(File tempFile, String jobId, int sourceSrid) {
        long startTime = System.currentTimeMillis();
        JOB_STATUS.put(jobId, "데이터 분석 및 저장 시작...");

        // [1] 유틸 호출: 인코딩 자동 감지
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
                    // 콤마 파싱 (따옴표 안 콤마 무시 정규식)
                    String[] rowData = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                    if (rowData.length <= Math.max(latIdx, lonIdx)) continue;

                    String latStr = rowData[latIdx].replace("\"", "").trim();
                    String lonStr = rowData[lonIdx].replace("\"", "").trim();
                    if (latStr.isEmpty() || lonStr.isEmpty()) continue;

                    double rawLat = Double.parseDouble(latStr);
                    double rawLon = Double.parseDouble(lonStr);

                    // [2] 유틸 호출: 좌표 변환 (5179 -> 4326 등)
                    double[] coords = CoordinateTransformer.transform(sourceSrid, rawLon, rawLat);
                    double finalLon = coords[0]; // x
                    double finalLat = coords[1]; // y

                    // Entity 생성
                    Shop shop = new Shop();
                    shop.setShopName(getVal(rowData, nameIdx));
                    shop.setCategoryMain(getVal(rowData, mainIdx));
                    shop.setCategorySub(getVal(rowData, subIdx));
                    shop.setAddress(getVal(rowData, addrIdx));
                    shop.setLat(finalLat);
                    shop.setLon(finalLon);
                    shop.setGeom(geometryFactory.createPoint(new Coordinate(finalLon, finalLat)));

                    batchList.add(shop);
                    count++;

                } catch (Exception e) {
                    continue; // 에러 행 무시
                }

                // [3] OOM 방지: 1000건마다 저장 후 메모리 비우기
                if (batchList.size() >= 1000) {
                    saveAndClear(batchList);
                    if (count % 10000 == 0) {
                        JOB_STATUS.put(jobId, count + "건 저장 중...");
                        log.info("Job {} - {}건 진행", jobId, count);
                    }
                }
            }

            if (!batchList.isEmpty()) {
                saveAndClear(batchList);
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

    private void saveAndClear(List<Shop> batchList) {
        shopRepository.saveAll(batchList);
        entityManager.flush(); // DB 전송
        entityManager.clear(); // 메모리 비우기
        batchList.clear();
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

    // [기능 2] 기존 분석 로직
    @Transactional(readOnly = true)
    public AnalysisResponse analyze(double lat, double lon, double radiusKm) {
        List<Shop> shops = shopRepository.findNearbyShops(lon, lat, radiusKm * 1000);

        List<AnalysisResponse.ShopItem> tList = new ArrayList<>();
        List<AnalysisResponse.ShopItem> mList = new ArrayList<>();
        List<AnalysisResponse.ShopItem> cList = new ArrayList<>();
        List<AnalysisResponse.ShopItem> fList = new ArrayList<>();
        List<AnalysisResponse.ShopItem> cafList = new ArrayList<>();

        for (Shop s : shops) {
            int dist = (int) calculateDistance(lat, lon, s.getLat(), s.getLon());
            String main = s.getCategoryMain() == null ? "" : s.getCategoryMain();
            String sub = s.getCategorySub() == null ? "" : s.getCategorySub();
            String name = s.getShopName() == null ? "" : s.getShopName();

            AnalysisResponse.ShopItem item = new AnalysisResponse.ShopItem(
                    s.getId(), name, main, sub, s.getLat(), s.getLon(), dist
            );

            //분류 로직
            if (main.contains("교통") && (sub.contains("지하철") || sub.contains("철도") || sub.contains("역") || sub.contains("여객"))) tList.add(item);
            else if (main.contains("의료") || sub.contains("병원") || sub.contains("약국") || sub.contains("의원")) {
                if (!sub.contains("동물") && !sub.contains("수의")) mList.add(item);
            }
            else if (sub.contains("편의점") || sub.contains("슈퍼") || sub.contains("마트") || (main.contains("소매") && (name.contains("24") || name.contains("GS") || name.contains("CU")))) cList.add(item);
            else if (sub.contains("카페") || sub.contains("커피") || sub.contains("찻집") || (main.contains("음식") && (name.contains("스타벅스") || name.contains("이디야") || name.contains("카페")))) cafList.add(item);
            else if (main.contains("음식")) fList.add(item);
        }

        sortListByDist(tList); sortListByDist(mList); sortListByDist(cList); sortListByDist(fList); sortListByDist(cafList);

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

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
        dist = Math.acos(dist);
        dist = Math.toDegrees(dist);
        dist = dist * 60 * 1.1515 * 1609.344;
        return dist;
    }
}