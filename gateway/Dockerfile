FROM eclipse-temurin:20-jdk-jammy AS builder
WORKDIR /app

# Отдельный слой: скачиваем дистрибутив Gradle (кешируется, пока wrapper не изменится)
COPY gradlew gradlew
COPY gradle/ gradle/
RUN chmod +x gradlew && \
    GRADLE_OPTS="-Xmx512m -Dfile.encoding=UTF-8" ./gradlew --version --no-daemon

# Отдельный слой: скачиваем зависимости (кешируется, пока build-файлы не изменятся)
COPY settings.gradle.kts build.gradle.kts ./
RUN GRADLE_OPTS="-Xmx1g -Dfile.encoding=UTF-8" ./gradlew dependencies --no-daemon 2>/dev/null || true

# Сборка fat JAR
COPY src/ src/
RUN GRADLE_OPTS="-Xmx1g -Dfile.encoding=UTF-8" ./gradlew buildFatJar --no-daemon

FROM eclipse-temurin:20-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*-all.jar app.jar
COPY --from=builder /app/src/main/resources/db/migration/ db/migration/
COPY entrypoint.sh .
RUN chmod +x entrypoint.sh
ENTRYPOINT ["./entrypoint.sh"]
