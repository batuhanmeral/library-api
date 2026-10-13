# The application as a container image, built in two stages:
#   docker build -t library-api .
# The first stage compiles and packages with the JDK; the second has only a JRE
# and the application, split into layers. docker-compose.yml runs it with
# PostgreSQL and Redis: docker compose --profile app up -d --build
#
# Both images are pinned by digest: a tag can be moved to another image, a digest
# cannot. Temurin 21.0.13+9 on Alpine.

FROM eclipse-temurin:21.0.13_9-jdk-alpine@sha256:82801f9dcdcbad5a05082aba8c86d1da5b72ccb498e71318b1f91b5a477887b3 AS build
WORKDIR /build

# The Maven Wrapper and the POM first: the layer that downloads Maven and the
# dependencies is rebuilt only when these change, not on every source change.
# lombok.config belongs to the build too: without it Lombok does not copy
# @Qualifier to constructors, and beans are injected by type alone.
COPY mvnw pom.xml lombok.config ./
COPY .mvn/ .mvn/
# The cache mount keeps ~/.m2 between builds on this machine; it is not part of
# the image.
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
# The tests and the format check run in CI (./mvnw verify); here only the jar is
# built. The tests would also need Docker inside the build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode --no-transfer-progress package -DskipTests -Djacoco.skip=true \
    && cp target/library-api-*.jar application.jar \
    # Split the jar: dependencies, Spring Boot's loader, snapshot dependencies and
    # the application's own classes, each in a directory of its own.
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21.0.13_9-jre-alpine@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474

# The base image is pinned, so it never gets Alpine's later security fixes by
# itself; apk upgrade installs them at build time (a rebuild picks up new ones).
# Then a user without a password, home directory or shell login; the process
# never runs as root. The numeric id lets a platform check "not root".
RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app

# One image layer per jar layer, from the least to the most often changed. A new
# version of the application changes only the last one, so a pull downloads a few
# hundred kilobytes instead of the whole dependency set.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=prod
# The JVM reads JAVA_TOOL_OPTIONS by itself; override it with docker run -e.
# Heap: three quarters of the container's memory limit. On OutOfMemoryError the
# process exits, so the container runtime can restart it, instead of limping on.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

# Liveness: is the process able to answer at all. Readiness (database) is for
# whoever routes traffic; a container should not count as broken while only the
# database is away. wget comes with Alpine's BusyBox.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
