# Сборка без локального Unix-gradlew: образ Gradle из Docker Hub.
#   docker build -t apofeoz-backend .
#   docker run --rm -p 8080:8080 -e JDBC_URL=... -e JWT_SECRET=... apofeoz-backend

FROM gradle:8.10.2-jdk21-alpine AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src ./src
RUN gradle shadowJar --no-daemon --max-workers=1 -Dorg.gradle.jvmargs="-Xmx768m" -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/build/libs/*-all.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
