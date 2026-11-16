# ── Build ────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Dependencies first, so a source-only change doesn't re-download the world. The cache mount
# keeps ~/.m2 between builds on the same Docker host (BuildKit, the default builder).
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline || true
COPY src ./src
# Tests are not run in the image build (they need a database); run `mvnw test` before deploying.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Dmaven.test.skip=true package \
 && cp target/*.jar /app.jar

# Split the fat jar into layers: third-party libraries (change rarely) end up in their own image
# layer, so a code-only rebuild pushes/pulls a few hundred KB instead of ~65 MB.
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /x
COPY --from=build /app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination layers

# ── Run ──────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
# tzdata: SLA business hours are computed in the JVM's local time zone (see BusinessCalendar),
# so the container must run in the same zone as the customers' working day — not UTC.
RUN apk add --no-cache tzdata \
 && addgroup -S app && adduser -S app -G app \
 && mkdir -p /app/media && chown -R app:app /app
ENV TZ=Asia/Kuala_Lumpur \
    MEDIA_PATH=/app/media \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
WORKDIR /app
COPY --from=extract --chown=app:app /x/layers/dependencies/ ./
COPY --from=extract --chown=app:app /x/layers/spring-boot-loader/ ./
COPY --from=extract --chown=app:app /x/layers/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /x/layers/application/ ./
USER app
EXPOSE 8080
VOLUME ["/app/media"]
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Duser.timezone=$TZ org.springframework.boot.loader.launch.JarLauncher"]
