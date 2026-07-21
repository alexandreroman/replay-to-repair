# Stage 1 — build the fat JAR from source (self-contained: no pre-build needed)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
# Warm the dependency cache on its own layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# Stage 2 — split the fat JAR into cacheable layers
FROM eclipse-temurin:25-jre AS extract
WORKDIR /build
COPY --from=build /src/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# Stage 3 — minimal runtime image, non-root
FROM eclipse-temurin:25-jre
WORKDIR /app
# curl is used by the Compose healthcheck to probe the actuator endpoint.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && addgroup --system app \
 && adduser --system --ingroup app app
COPY --from=extract --chown=app:app /build/extracted/dependencies/ ./
COPY --from=extract --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /build/extracted/application/ ./
USER app:app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
