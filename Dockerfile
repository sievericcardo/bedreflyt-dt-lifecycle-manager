# Use an official OpenJDK runtime as a parent image
FROM openjdk:24-oraclelinux8

# Set the working directory in the container
WORKDIR /app

# Copy the executable jar file to the container
COPY bedreflyt-lm.jar /app/bedreflyt-lm.jar

# Expose the port that the application will run on
EXPOSE 8091

# Run the jar file
ENTRYPOINT ["java", "-jar", "/app/bedreflyt-lm.jar"]