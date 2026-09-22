# ---- Stage 1: Build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Maven wrapper and POM first (layer caching for dependencies)
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src src
RUN ./mvnw package -DskipTests -B

# ---- Stage 2: Run ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Environment variables (injected at runtime via docker run --env-file or -e)
ENV R2BC_URL=""
ENV R2BC_USERNAME=""
ENV R2BC_PASSWORD=""
ENV ENGINE_CLUSTER_NODES="standalone"
ENV FRONTEND_URL="http://localhost:5173"
ENV SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
