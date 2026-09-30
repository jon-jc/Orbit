FROM eclipse-temurin:25-jdk-jammy AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp verify

FROM eclipse-temurin:25-jre-jammy AS runtime
RUN apt-get update && apt-get upgrade -y && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 orbit \
    && useradd --uid 10001 --gid orbit --no-create-home --shell /usr/sbin/nologin orbit
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/orbit.jar
USER 10001:10001
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:9091/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app/orbit.jar"]
