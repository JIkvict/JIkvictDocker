# JIkvict Docker Solution Runner

A Docker-based solution for extracting ZIP archives and running Gradle tasks. This project is a proof of concept (POC) that demonstrates how to:

1. Extract a ZIP archive containing a Gradle project
2. Find the project directory with the Gradle wrapper
3. Run the Gradle test task
4. Handle various error conditions and edge cases

## Project Structure

- `src/main/kotlin/org/jikvict/SolutionRunner.kt`: The main Kotlin script that handles ZIP extraction and Gradle task execution
- `build.gradle`: Gradle build configuration
- `Dockerfile`: Docker container configuration

## Building the Project

### Prerequisites

- JDK 21 or later
- Gradle 8.0 or later

### Build Steps

1. Clone the repository
2. Build the project with Gradle:

```bash
./gradlew clean build
```

This will create an executable JAR file in the `build/libs` directory.

## Using the Docker Container

### Building the Docker Image

After building the project, you can build the Docker image:

```bash
docker build -t jikvict-solution-runner .
```

### Running the Docker Container

To run the container with a ZIP file:

```bash
# Mount a directory containing your ZIP file and run the container
docker run --rm -v /path/to/your/zip/directory:/app/input jikvict-solution-runner /app/input/your-solution.zip 300
```

Where:
- `/path/to/your/zip/directory` is the path to the directory containing your ZIP file
- `your-solution.zip` is the name of your ZIP file
- `300` is the timeout in seconds (optional, default is 300)

### Using Dependency Caching

To speed up builds by caching Gradle dependencies between container runs:

```bash
# Create a named volume for Gradle cache
docker volume create gradle-cache

# Run with both input directory and cache volume mounted
docker run --rm \
  -v /path/to/your/zip/directory:/app/input \
  -v gradle-cache:/gradle-cache \
  jikvict-solution-runner /app/input/your-solution.zip 300
```

This will:
- Store Gradle dependencies in a persistent volume
- Reuse cached dependencies in subsequent runs
- Significantly reduce build time for repeated builds

## Running Without Docker

You can also run the solution directly without Docker:

```bash
java -jar build/libs/JIkvictDocker-1.0-SNAPSHOT.jar /path/to/your/solution.zip [timeout-seconds]
```

## How It Works

1. The solution extracts the provided ZIP archive to a temporary directory
2. It searches for a Gradle project directory (identified by the presence of a `gradlew` file)
3. It runs the Gradle test task with appropriate parameters
4. It captures and displays the output of the Gradle task
5. It cleans up temporary files when done

## Features

- Robust ZIP extraction with fallback mechanisms for problematic archives (especially from macOS)
- Path traversal protection
- Timeout handling for long-running tasks
- Detailed logging of the extraction and execution process
- Automatic cleanup of temporary files
- Gradle dependency caching for faster builds

## Customization

You can customize the behavior by modifying the following:

- The Gradle task to run (currently set to `test`)
- The timeout duration
- The Docker base image
- Additional dependencies or tools in the Docker container

## Future Enhancements

- Support for additional Gradle tasks
- More detailed reporting of test results
- Integration with CI/CD pipelines
- Support for other build systems beyond Gradle