# Multi-stage build: compile with Maven, run on a slim JRE as non-root.

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -B -DskipTests package \
    && cp target/paylens-api-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache curl \
    && addgroup -S paylens \
    && adduser -S -G paylens paylens

WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
RUN chown paylens:paylens /app/app.jar

USER paylens
EXPOSE 8081

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=12 \
  CMD curl -fsS http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
