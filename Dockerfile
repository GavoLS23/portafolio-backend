# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.1_12_1.9.7_3.3.1 AS builder

WORKDIR /app

# Cache de dependencias
COPY build.sbt .
COPY project/build.properties project/
COPY project/plugins.sbt project/
RUN sbt update

# Compilar y empaquetar
COPY src src
RUN sbt assembly

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Usuario no-root
RUN addgroup -S portafolio && adduser -S portafolio -G portafolio
USER portafolio

COPY --from=builder /app/target/scala-3.3.1/portafolio-backend-assembly-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
