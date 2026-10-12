# 모든 서비스가 공유하는 이미지. 빌드 단계는 한 번만 돌고(캐시 공유), 실행 단계에서 MODULE 의 jar 만 꺼낸다.
#   docker build --build-arg MODULE=distributed-chat-system-chatting .

# ── 빌드 ────────────────────────────────────────────────────────
FROM gradle:8.10.2-jdk17 AS builder

WORKDIR /app

COPY gradle gradle/
COPY gradlew settings.gradle build.gradle /app/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --version > /dev/null

COPY . .
# 테스트는 이미지 빌드에서 돌리지 않는다
RUN --mount=type=cache,target=/root/.gradle \
    GRADLE_USER_HOME=/root/.gradle ./gradlew --no-daemon bootJar -x test \
    && mkdir /jars \
    && find . -path '*/build/libs/*.jar' ! -name '*-plain.jar' -exec cp {} /jars/ \;

# ── 실행 ────────────────────────────────────────────────────────
FROM amazoncorretto:17

# 예) distributed-chat-system-chatting, distributed-chat-system-api-gateway
ARG MODULE
ENV TZ=Asia/Seoul
COPY --from=builder /jars/${MODULE}-0.0.1-SNAPSHOT.jar /app.jar

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app.jar"]
