# ── Stage 1: Build ────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Render injects $PORT at runtime; default to 8080 for local runs
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=default

COPY --from=builder /app/target/clearpath-backend-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -Dserver.port=$PORT -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar app.jar"]
