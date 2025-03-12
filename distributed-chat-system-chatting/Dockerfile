FROM gradle:8.10.2-jdk17 AS builder

WORKDIR /app

COPY gradle gradle/
COPY gradlew settings.gradle build.gradle /app/
RUN ./gradlew :distributed-chat-system-chatting:dependencies --no-daemon

COPY . .
RUN ./gradlew :distributed-chat-system-chatting:bootJar --no-daemon

FROM amazoncorretto:17
COPY --from=builder /app/distributed-chat-system-chatting/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]