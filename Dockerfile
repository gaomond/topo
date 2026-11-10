FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
RUN ./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process bootJar \
    && jar_path=$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar') \
    && test -n "$jar_path" \
    && cp "$jar_path" /workspace/app.jar

FROM eclipse-temurin:25-jre-noble AS runtime

RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar ./app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
