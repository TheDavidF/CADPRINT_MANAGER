FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src/main ./src/main
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
RUN mkdir -p /data/images /data/documents && chown -R 10001:10001 /data
WORKDIR /app
COPY --from=build /src/target/*.jar /app/cadprint.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/cadprint.jar"]
