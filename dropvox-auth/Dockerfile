# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .

COPY dropvox-auth/pom.xml dropvox-auth/pom.xml
RUN mvn --file dropvox-auth/pom.xml dependency:go-offline

COPY dropvox-auth/src dropvox-auth/src
RUN mvn --file dropvox-auth/pom.xml clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/dropvox-auth/target/dropvox-auth.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]