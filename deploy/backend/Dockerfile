FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY services/api/pom.xml services/api/pom.xml
COPY services/api/src services/api/src
RUN mvn --batch-mode -f services/api/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre

RUN groupadd --gid 10001 finals \
    && useradd --uid 10001 --gid 10001 --no-create-home \
      --home-dir /app --shell /usr/sbin/nologin finals

WORKDIR /app

# Named volumes created on a new server inherit these directory owners. This lets
# the API write uploads and rolling logs without ever starting the JVM as root.
RUN mkdir -p /app/uploads /app/logs \
    && chown -R 10001:10001 /app

COPY --from=build --chown=10001:10001 \
  /workspace/services/api/target/finals-compass-api-0.1.0.jar /app/app.jar

USER 10001:10001

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
