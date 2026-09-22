# Stage 1: Build stage
FROM eclipse-temurin:17.0.20_8-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper configuration and dependencies descriptor
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings for Linux and download dependencies (layer cache)
RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copy application source code
COPY src/ src/

# Compile and package application (skipping tests for container builds)
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:17.0.20_8-jre-alpine

WORKDIR /app

# Create non-root user and group for security
RUN addgroup -S spring && adduser -S spring -G spring

# Copy compiled JAR from builder stage
COPY --from=builder /build/target/*.jar /app/app.jar

# Set correct ownership
RUN chown -R spring:spring /app

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
