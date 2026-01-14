-- schema.sql

-- 1. 필수 확장 기능 활성화
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 2. 상권 데이터 테이블
CREATE TABLE IF NOT EXISTS shop_data (
    id BIGSERIAL PRIMARY KEY,
    shop_name VARCHAR(150),
    category_main VARCHAR(50),
    category_sub VARCHAR(50),
    address VARCHAR(255),
    lat FLOAT8 NOT NULL,
    lon FLOAT8 NOT NULL,
    score FLOAT8 DEFAULT 0,
    geom GEOMETRY(Point, 4326)
);

-- 3. 공간 인덱스 (GIST)
CREATE INDEX IF NOT EXISTS idx_shop_category_geom ON shop_data USING GIST (category_main, geom);
CREATE INDEX IF NOT EXISTS idx_shop_geom ON shop_data USING GIST (geom);

-- 4. 관리자 테이블 (처음부터 모든 컬럼 포함)
CREATE TABLE IF NOT EXISTS admin_member (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20),
    failed_attempts INT DEFAULT 0,    -- 실패 횟수
    lock_time TIMESTAMP,             -- 잠긴 시간
    is_locked BOOLEAN DEFAULT false  -- 잠금 여부
);