# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Dependencies step for caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build package
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create folder for persistent media uploads
RUN mkdir -p /app/uploads

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Relaxed JVM heap limit for smooth local performance
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]