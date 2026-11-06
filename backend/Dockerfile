FROM eclipse-temurin:23-jdk-alpine AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY gradlew ./
COPY backend ./backend
COPY cli ./cli
COPY gui ./gui
RUN chmod +x gradlew && ./gradlew :backend:bootJar -x test --no-daemon

FROM eclipse-temurin:23-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl \
    && adduser -D riseup
USER riseup
COPY --from=build /workspace/backend/build/libs/riseup-backend-*.jar /app/app.jar
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=12 \
  CMD curl -fsS http://127.0.0.1:8000/api/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
