FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/pettracker-*.jar /tmp/libs/
RUN set -eu; \
    for jar in /tmp/libs/pettracker-*.jar; do \
      case "$jar" in \
        *-plain.jar) ;; \
        *) cp "$jar" /app/app.jar ;; \
      esac; \
    done; \
    test -f /app/app.jar; \
    rm -rf /tmp/libs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
