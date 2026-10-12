# 모든 서비스가 공유하는 이미지. 빌드 단계는 한 번만 돌고(캐시 공유), 실행 단계에서 MODULE 의 jar 만 꺼낸다.
#   docker build --build-arg MODULE=distributed-chat-system-chatting .

# ── 빌드 ────────────────────────────────────────────────────────
FROM gradle:8.10.2-jdk17 AS builder

# Node 는 프론트(vite)만 쓴다. 우분투 기본 패키지는 낡아서 vite 6 이 안 돈다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY gradle gradle/
COPY gradlew settings.gradle build.gradle /app/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --version > /dev/null

# npm 의존성을 먼저 받아 둔다. package.json 이 그대로면 이 층을 다시 타지 않는다.
COPY frontend/package.json frontend/package-lock.json frontend/
RUN cd frontend && npm ci --no-audit --no-fund

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
