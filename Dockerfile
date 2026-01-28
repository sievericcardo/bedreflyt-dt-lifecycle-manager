# Build stage
FROM gradle:8.5-jdk21 AS build

# Set the working directory
WORKDIR /app

# Copy gradle files
COPY build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle

# Copy source code
COPY src ./src

# Build the application
RUN gradle bootJar --no-daemon && \
    rm -f /app/build/libs/*-plain.jar

# Runtime stage
FROM openjdk:24-ea-oraclelinux8

# Set the working directory in the container
WORKDIR /app

# Copy the executable jar file from build stage
COPY --from=build /app/build/libs/bedreflyt-lm-*.jar /app/bedreflyt-lm.jar

# Expose the port that the application will run on
EXPOSE 8091

# Run the jar file
ENTRYPOINT ["java", "-jar", "/app/bedreflyt-lm.jar"]