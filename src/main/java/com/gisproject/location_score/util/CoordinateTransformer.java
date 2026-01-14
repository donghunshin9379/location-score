package com.gisproject.location_score.util;

import org.locationtech.proj4j.*;

public class CoordinateTransformer {
    private static final CRSFactory crsFactory = new CRSFactory();
    private static final CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();

    // 정의 문자열
    private static final String WGS84_PARAM = "+proj=longlat +datum=WGS84 +no_defs";
    private static final String EPSG5179_PARAM = "+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 +x_0=1000000 +y_0=2000000 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs";

    // [최적화] 변환 객체를 미리 만들어둠 (5179 -> 4326 전용)
    private static final CoordinateTransform TRANSFORM_5179_TO_4326;

    static {
        CoordinateReferenceSystem source = crsFactory.createFromParameters("EPSG:5179", EPSG5179_PARAM);
        CoordinateReferenceSystem target = crsFactory.createFromParameters("EPSG:4326", WGS84_PARAM);
        TRANSFORM_5179_TO_4326 = ctFactory.createTransform(source, target);
    }

    public static double[] transform(int sourceEpsg, double x, double y) {
        if (sourceEpsg == 4326) {
            return new double[]{x, y};
        }

        ProjCoordinate srcCoord = new ProjCoordinate(x, y);
        ProjCoordinate destCoord = new ProjCoordinate();

        // 미리 만들어둔 변환기 사용 (객체 생성 비용 절약)
        if (sourceEpsg == 5179) {
            TRANSFORM_5179_TO_4326.transform(srcCoord, destCoord);
        } else {
            // 혹시 다른 좌표계가 들어오면 그때만 새로 생성 (예외 처리)
            return new double[]{x, y};
        }

        return new double[]{destCoord.x, destCoord.y};
    }
}