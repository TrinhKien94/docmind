# === Stage 1: Build ===
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom + tải dependency trước (cache layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source + build (skip test trong image build)
COPY src ./src
RUN mvn clean package -DskipTests -B

# === Stage 2: Runtime ===
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
