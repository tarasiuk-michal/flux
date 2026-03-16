# Build stage
FROM maven:3.9-eclipse-temurin-21 as builder

WORKDIR /build

# Copy pom.xml only first (for dependency caching)
COPY pom.xml .

# Download dependencies (this layer is cached if pom.xml doesn't change)
# Use go-offline to ensure all dependencies are downloaded
RUN mvn dependency:go-offline -q || true

# Copy source code
COPY src src

# Build application (skip tests for faster builds)
RUN mvn clean package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy built JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Expose port
EXPOSE 8881

# Health check (wait 45s before first check, then every 10s)
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=45s \
    CMD curl -f http://localhost:8881/api/health || exit 1

# Run application with optimized startup settings
ENTRYPOINT ["java", "-XX:+UseStringDeduplication", "-Dspring.jmx.enabled=false", "-jar", "app.jar"]
