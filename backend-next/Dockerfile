FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S face \
    && adduser -S -G face face
WORKDIR /app
COPY --from=build --chown=face:face /workspace/target/face-chain-platform-0.1.0-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /app/adapters /app/data/media \
    && chown -R face:face /app/adapters /app/data
USER face
EXPOSE 8090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-Dloader.path=/app/adapters", "-cp", "/app/app.jar", "org.springframework.boot.loader.launch.PropertiesLauncher"]
