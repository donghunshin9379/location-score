# 1. 베이스 이미지 선택 (openjdk 보다 경량화되고 보안이 강화된 eclipse-temurin 권장)
FROM eclipse-temurin:17-jre-jammy

# 2. 한국 시간대 설정 (GIS 데이터의 생성/수정 시간 기록 시 중요)
ENV TZ=Asia/Seoul
RUN apt-get update && apt-get install -y tzdata && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 3. JAR 파일 복사
# 빌드된 jar 파일 이름이 유동적일 수 있으므로 와일드카드 사용 권장
COPY build/libs/*.jar app.jar

# 4. 실행 (운영 프로필 적용 권장)
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "/app.jar"]