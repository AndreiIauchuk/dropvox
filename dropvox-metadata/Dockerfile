# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .

COPY dropvox-metadata/pom.xml dropvox-metadata/pom.xml
RUN mvn --file dropvox-metadata/pom.xml dependency:go-offline

COPY dropvox-metadata/src dropvox-metadata/src
RUN mvn --file dropvox-metadata/pom.xml clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/dropvox-metadata/target/dropvox-metadata.jar app.jar
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
