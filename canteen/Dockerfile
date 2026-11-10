# Một image chạy cả giao diện và server: Spring Boot phục vụ luôn web_client đã build.
# Build từ thư mục gốc của dự án (nơi có server/ và web_client/).

# ---- 1) Build giao diện React ----
FROM node:20-alpine AS web
WORKDIR /web
COPY web_client/package*.json ./
RUN npm ci
COPY web_client/ ./
RUN npm run build

# ---- 2) Build server, nhúng giao diện vào static ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY server/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY server/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# ---- 3) Chạy ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx350m -XX:+UseSerialGC"
EXPOSE 8080
# Render cấp cổng qua biến PORT.
CMD ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
