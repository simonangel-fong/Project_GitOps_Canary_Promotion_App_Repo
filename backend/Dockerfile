FROM eclipse-temurin:25.0.3_9-jdk-ubi10-minimal@sha256:69dacb80fd8ecdcda554d0e80e4da22e5bf7737981b2a0aea26a135552d8abe2 AS builder
WORKDIR /app
COPY app/mvnw app/pom.xml app/checkstyle.xml app/dependency-check-suppressions.xml ./
COPY app/.mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q
COPY app/src src
RUN ./mvnw package -DskipTests -q && mv target/*.jar target/app.jar

FROM eclipse-temurin:25.0.3_9-jre-ubi10-minimal@sha256:f03be975d9c9198ef47f0abcf9ac9f12e803d0243c604732f35d7676ee1ffe68
WORKDIR /app
COPY --from=builder /app/target/app.jar app.jar
RUN groupadd --system --gid 1001 appgroup \
 && useradd --system --uid 1001 --gid appgroup --no-create-home appuser
USER 1001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
