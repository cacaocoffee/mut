# API 컨테이너 (ADR-0007 2단계).
#
# 2단 빌드다 — 빌드는 JDK 21 + gradlew(저장소가 래퍼 버전을 정한다), 실행은 JRE 만.
# 의존성 내려받기를 소스 복사보다 앞에 둬서, 코드만 바뀐 빌드는 캐시를 탄다.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
COPY openapi.json ./
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app

# 컨테이너 메모리의 75% 까지만 힙으로 쓴다 — 나머지는 메타스페이스·스레드 몫이다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
