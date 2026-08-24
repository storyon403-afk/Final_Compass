FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY services/api/pom.xml services/api/pom.xml
COPY services/api/src services/api/src
RUN mvn --batch-mode -f services/api/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/services/api/target/finals-compass-api-0.1.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
