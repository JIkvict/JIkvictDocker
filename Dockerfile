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

# Install Gradle
ENV GRADLE_VERSION=8.13
ENV GRADLE_HOME=/opt/gradle
ENV PATH=${GRADLE_HOME}/bin:${PATH}
ENV GRADLE_USER_HOME=/gradle-cache

RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O gradle.zip && \
    mkdir -p ${GRADLE_HOME} && \
    unzip -q gradle.zip -d /opt && \
    mv /opt/gradle-${GRADLE_VERSION}/* ${GRADLE_HOME} && \
    rm -rf /opt/gradle-${GRADLE_VERSION} && \
    rm gradle.zip && \
    mkdir -p ${GRADLE_USER_HOME}

# Create a directory for input files
RUN mkdir -p /app/input

# Copy only the build files first to cache dependencies
COPY build.gradle settings.gradle gradle.properties /app/
COPY gradle /app/gradle

# Copy source code
COPY src /app/src

# Build the project with cached dependencies
RUN gradle build --no-daemon

# Set the entrypoint to run the JAR
ENTRYPOINT ["java", "-jar", "/app/solution-runner.jar"]

# Default command if no arguments are provided
CMD ["/app/input/solution.zip", "300"]

# Instructions for running with dependency caching:
# docker build -t jikvict-docker .
# docker run -v gradle-cache:/gradle-cache jikvict-docker