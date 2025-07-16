FROM eclipse-temurin:21-jdk

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY build/libs/JIkvictDocker-1.0-SNAPSHOT.jar /app/solution-runner.jar

# Install unzip for better compatibility with macOS ZIP files
RUN apt-get update && \
    apt-get install -y unzip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create a directory for input files
RUN mkdir -p /app/input

# Set the entrypoint to run the JAR
ENTRYPOINT ["java", "-jar", "/app/solution-runner.jar"]

# Default command if no arguments are provided
CMD ["/app/input/solution.zip", "300"]