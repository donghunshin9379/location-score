# 📍 Location Score (입지 분석 플랫폼)

> 공간 정보 분석 서비스입니다. 

## 🔗 서비스 링크
* **운영 서버**: [입지 점수 Location Score](http://52.78.240.218:8080) (AWS EC2 운영 중) 

## 🛠 핵심 기술 스택
* **Backend**: Java, Spring Boot, JPA
* **GIS**: PostGIS, Leaflet.js
* **Infra**: AWS (EC2/RDS), Docker

## 🚀 주요 성과 (Troubleshooting)
### 1. 50만 건 대용량 데이터 처리 및 성능 최적화 
* GIST 공간 인덱스를 적용하여 조회 속도를 **50% 향상 (5s -> 2s)** 시켰습니다. 

### 2. 인프라 한계 극복 (JVM 튜닝) [cite: 23, 34]
* **문제**: AWS 프리티어(RAM 1GB) 환경에서 대용량 데이터 적재 시 OOM(Out Of Memory) 발생. 
* **해결**: **JVM 힙 메모리 최적화(-Xmx512m)** 및 배치 처리 도입으로 시스템 안정성을 확보했습니다. 

### 3. 서비스 안정성 확보 (Validation)
* 피드백을 통해 발견한 **광범위 검색 시 서버 부하 문제**를 백엔드 반경 제한(1km) 로직으로 해결했습니다.