# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .

COPY dropvox-gateway/pom.xml dropvox-gateway/pom.xml
RUN mvn --file dropvox-gateway/pom.xml dependency:go-offline

COPY dropvox-gateway/src dropvox-gateway/src
RUN mvn --file dropvox-gateway/pom.xml clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/dropvox-gateway/target/dropvox-gateway.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]