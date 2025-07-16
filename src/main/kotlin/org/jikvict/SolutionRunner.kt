package org.jikvict

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

/**
 * Entry point for the solution runner application.
 * Parses command line arguments and executes the solution runner.
 */
fun main(args: Array<String>) {
    val arguments = parseArguments(args) ?: run {
        displayUsage()
        exitProcess(1)
    }
    
    Logger.info("Processing zip file: ${arguments.zipFilePath} with timeout: ${arguments.timeoutSeconds} seconds")
    
    val solutionRunner = SolutionRunner()
    solutionRunner.executeCode(arguments.zipFile, arguments.timeoutSeconds)
}

/**
 * Displays usage information for the application.
 */
private fun displayUsage() {
    Logger.info("Usage: SolutionRunner <zip-file-path> [timeout-seconds]")
    Logger.info("  <zip-file-path>: Path to the zip archive containing the Gradle project")
    Logger.info("  [timeout-seconds]: Optional timeout in seconds (default: 300)")
}

/**
 * Parses command line arguments and validates them.
 * 
 * @param args Command line arguments
 * @return Arguments object or null if arguments are invalid
 */
private fun parseArguments(args: Array<String>): Arguments? {
    if (args.isEmpty()) {
        return null
    }

    val zipFilePath = args[0]
    val timeoutSeconds = if (args.size > 1) args[1].toLongOrNull() ?: 300L else 300L
    
    val zipFile = File(zipFilePath)
    if (!zipFile.exists()) {
        Logger.error("File not found: $zipFilePath")
        return null
    }
    
    return Arguments(zipFilePath, zipFile, timeoutSeconds)
}

/**
 * Data class to hold parsed command line arguments.
 */
data class Arguments(
    val zipFilePath: String,
    val zipFile: File,
    val timeoutSeconds: Long
)

/**
 * Simple logger to centralize logging functionality.
 */
object Logger {
    fun info(message: String) = println(message)
    fun error(message: String) = println("Error: $message")
    fun debug(message: String) = println(message)
    fun warning(message: String) = println("Warning: $message")
}

/**
 * Main class responsible for executing code from a zip file.
 * Orchestrates the extraction, project discovery, and execution process.
 */
class SolutionRunner {
    /**
     * Executes code from a zip file with the specified timeout.
     * 
     * @param file Zip file containing the code to execute
     * @param timeoutSeconds Timeout in seconds for execution
     */
    fun executeCode(file: File, timeoutSeconds: Long) {
        val executionId = UUID.randomUUID().toString()
        val tempDir = Files.createTempDirectory("code-$executionId")

        try {
            // Extract and process the zip file
            val zipExtractor = ZipExtractor()
            Logger.info("Extracting zip file to: $tempDir")
            zipExtractor.extract(file, tempDir)
            
            // Display the directory structure
            DirectoryUtils.logTreeStructure(tempDir)

            // Find the project directory
            val projectDir = findProjectDirectory(tempDir) ?: throw ProjectNotFoundException()
            Logger.info("Found project directory: $projectDir")

            // Make gradlew executable
            val gradlewFile = projectDir.resolve("gradlew")
            gradlewFile.toFile().setExecutable(true)

            // Run the Gradle task
            val gradleRunner = GradleRunner()
            gradleRunner.runGradleTask(projectDir, timeoutSeconds)
        } catch (_: ProjectNotFoundException) {
            Logger.error("Project directory with gradlew not found in the archive")
        } catch (e: Exception) {
            Logger.error("Error executing code: ${e.message}")
            e.printStackTrace()
        } finally {
            DirectoryUtils.cleanupDirectory(tempDir)
        }
    }

    /**
     * Finds the project directory containing a gradlew file.
     * 
     * @param baseDir Base directory to search in
     * @return Path to the project directory or null if not found
     */
    private fun findProjectDirectory(baseDir: Path): Path? {
        Logger.info("Searching for project directory in: $baseDir")

        return try {
            Files.walk(baseDir)
                .filter { path -> Files.isDirectory(path) && Files.exists(path.resolve("gradlew")) }
                .findFirst()
                .orElse(null)
        } catch (e: Exception) {
            Logger.error("Error searching for project directory: ${e.message}")
            null
        }
    }
}

/**
 * Exception thrown when a project directory is not found.
 */
class ProjectNotFoundException : RuntimeException("Project directory with gradlew not found in the archive")

/**
 * Utility class for directory operations.
 */
object DirectoryUtils {
    /**
     * Cleans up a directory by deleting all files and subdirectories.
     * 
     * @param directory Directory to clean up
     */
    fun cleanupDirectory(directory: Path) {
        try {
            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::delete)
        } catch (e: Exception) {
            Logger.error("Error cleaning up temporary directory: ${e.message}")
        }
    }

    /**
     * Logs the directory structure for debugging purposes.
     * 
     * @param directory Directory to log
     * @param level Current indentation level
     */
    fun logTreeStructure(directory: Path, level: Int = 0) {
        val indent = "  ".repeat(level)
        Logger.debug("$indent${directory.fileName}")

        Files.list(directory).use { paths ->
            paths.forEach { file ->
                if (Files.isDirectory(file)) {
                    logTreeStructure(file, level + 1)
                } else {
                    Logger.debug("$indent  ${file.fileName}")
                }
            }
        }
    }
}

/**
 * Class responsible for running Gradle tasks.
 */
class GradleRunner {
    /**
     * Runs a Gradle task in the specified project directory with a timeout.
     * 
     * @param projectDir Project directory containing the Gradle wrapper
     * @param timeoutSeconds Timeout in seconds
     */
    fun runGradleTask(projectDir: Path, timeoutSeconds: Long) {
        val relativeProjectPath = projectDir.toString()
        Logger.info("Relative project path: $relativeProjectPath")

        val processBuilder = ProcessBuilder()
            .directory(projectDir.toFile())
            .command("./gradlew", "test", "--no-daemon", "--console=plain")
            .redirectErrorStream(true)
        
        Logger.info("Executing Gradle task...")
        val process = processBuilder.start()
        
        // Capture output
        val output = process.inputStream.bufferedReader().use { it.readText() }
        
        // Wait for the process to complete with timeout
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        
        if (!completed) {
            handleTimeout(process, timeoutSeconds, output)
        } else {
            handleCompletion(process.exitValue(), output)
        }
    }

    /**
     * Handles the case when the process times out.
     * 
     * @param process The process that timed out
     * @param timeoutSeconds The timeout that was exceeded
     * @param output The partial output from the process
     */
    private fun handleTimeout(process: Process, timeoutSeconds: Long, output: String) {
        Logger.error("Execution timed out after $timeoutSeconds seconds")
        process.destroy()
        
        // Give the process a chance to terminate gracefully
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
        
        Logger.info("Process was forcibly terminated")
        Logger.info("Partial output:\n$output")
    }

    /**
     * Handles the completion of the process.
     * 
     * @param exitCode The exit code of the process
     * @param output The output from the process
     */
    private fun handleCompletion(exitCode: Int, output: String) {
        if (exitCode == 0) {
            Logger.info("Code executed successfully. Exit code: 0")
        } else {
            Logger.error("Code execution failed. Exit code: $exitCode")
        }
        Logger.info("Execution logs:\n$output")
    }
}

/**
 * Class responsible for extracting ZIP archives.
 */
class ZipExtractor {
    /**
     * Extracts a ZIP file to the target directory.
     * 
     * @param file ZIP file to extract
     * @param targetDir Target directory to extract to
     */
    fun extract(file: File, targetDir: Path) {
        try {
            extractZipFile(file, targetDir)
            Logger.info("ZIP archive successfully extracted")
        } catch (e: java.util.zip.ZipException) {
            handleZipException(e, file, targetDir)
        } catch (e: Exception) {
            Logger.error("Unexpected error during ZIP extraction: ${e.message}")
            throw RuntimeException("Failed to extract ZIP archive: ${e.message}", e)
        }
    }

    /**
     * Handles ZIP exceptions by attempting alternative extraction methods.
     * 
     * @param e The ZIP exception
     * @param file The ZIP file
     * @param targetDir The target directory
     */
    private fun handleZipException(e: java.util.zip.ZipException, file: File, targetDir: Path) {
        val message = e.message ?: ""
        if (message.contains("EXT descriptor") || message.contains("DEFLATED")) {
            Logger.warning("ZIP compatibility issue detected (likely from macOS): ${e.message}")
            Logger.info("Attempting to extract using alternative method...")
            extractWithCompatibility(file, targetDir)
        } else {
            Logger.error("ZIP extraction failed: ${e.message}")
            throw RuntimeException("Failed to extract ZIP archive: ${e.message}", e)
        }
    }

    /**
     * Extracts a ZIP file using the standard Java API.
     * 
     * @param file ZIP file to extract
     * @param targetDir Target directory to extract to
     */
    private fun extractZipFile(file: File, targetDir: Path) {
        ZipInputStream(file.inputStream()).use { zipStream ->
            var entry: ZipEntry?
            while (zipStream.nextEntry.also { entry = it } != null) {
                entry?.let { zipEntry ->
                    processZipEntry(zipEntry, zipStream, targetDir)
                }
                zipStream.closeEntry()
            }
        }
    }

    /**
     * Processes a single ZIP entry.
     * 
     * @param zipEntry The ZIP entry to process
     * @param zipStream The ZIP input stream
     * @param targetDir The target directory
     */
    private fun processZipEntry(zipEntry: ZipEntry, zipStream: ZipInputStream, targetDir: Path) {
        val entryPath = targetDir.resolve(zipEntry.name)

        // Check for path traversal
        if (!entryPath.startsWith(targetDir)) {
            Logger.warning("Path traversal attempt detected: ${zipEntry.name}")
            return
        }

        if (zipEntry.isDirectory) {
            Files.createDirectories(entryPath)
            Logger.debug("Created directory: ${zipEntry.name}")
        } else {
            extractFile(zipEntry, zipStream, entryPath)
        }
    }

    /**
     * Extracts a file from a ZIP entry.
     * 
     * @param zipEntry The ZIP entry containing the file
     * @param zipStream The ZIP input stream
     * @param entryPath The path to extract the file to
     */
    private fun extractFile(zipEntry: ZipEntry, zipStream: ZipInputStream, entryPath: Path) {
        // Create parent directories if they don't exist
        Files.createDirectories(entryPath.parent)

        // Copy file contents
        Files.copy(zipStream, entryPath)
        Logger.debug("Extracted file: ${zipEntry.name}")

        // Set executable permissions for gradlew
        if (zipEntry.name.endsWith("gradlew")) {
            entryPath.toFile().setExecutable(true)
            Logger.debug("Set executable permissions for: ${zipEntry.name}")
        }
    }

    /**
     * Extracts a ZIP file using the system unzip command for better compatibility.
     * 
     * @param file ZIP file to extract
     * @param targetDir Target directory to extract to
     */
    private fun extractWithCompatibility(file: File, targetDir: Path) {
        try {
            // Try using the unzip command first
            if (extractWithUnzipCommand(file, targetDir)) {
                return
            }
            
            // Fall back to manual extraction if unzip fails
            Logger.info("Switching to manual extraction...")
            extractManually(file, targetDir)
        } catch (e: Exception) {
            Logger.error("Error during compatibility extraction: ${e.message}")
            throw RuntimeException("Failed to extract ZIP archive: ${e.message}", e)
        }
    }

    /**
     * Extracts a ZIP file using the system unzip command.
     * 
     * @param file ZIP file to extract
     * @param targetDir Target directory to extract to
     * @return true if extraction was successful, false otherwise
     */
    private fun extractWithUnzipCommand(file: File, targetDir: Path): Boolean {
        Logger.info("Attempting extraction with unzip command")
        
        try {
            val processBuilder = ProcessBuilder("unzip", "-q", "-o", file.absolutePath, "-d", targetDir.toString())
                .redirectErrorStream(true)
            
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                Logger.warning("Unzip command exited with code $exitCode. Output: $output")
                return false
            }

            // Count and report extracted files
            val extractedCount = countExtractedFiles(targetDir)
            Logger.info("Extracted $extractedCount files using unzip")

            // Set executable permissions for gradlew if it exists
            setGradlewExecutable(targetDir)
            return true
        } catch (e: Exception) {
            Logger.warning("Error using unzip command: ${e.message}")
            return false
        }
    }

    /**
     * Counts the number of files extracted to a directory.
     * 
     * @param targetDir The directory containing the extracted files
     * @return The number of files
     */
    private fun countExtractedFiles(targetDir: Path): Int {
        return Files.walk(targetDir)
            .filter { !Files.isDirectory(it) }
            .count()
            .toInt()
    }

    /**
     * Sets the executable permission on gradlew files.
     * 
     * @param targetDir The directory to search for gradlew files
     */
    private fun setGradlewExecutable(targetDir: Path) {
        Files.walk(targetDir)
            .filter { !Files.isDirectory(it) && it.fileName.toString() == "gradlew" }
            .forEach { it.toFile().setExecutable(true) }
    }

    /**
     * Extracts a ZIP file manually, handling problematic entries.
     * 
     * @param file ZIP file to extract
     * @param targetDir Target directory to extract to
     */
    private fun extractManually(file: File, targetDir: Path) {
        Logger.info("Performing manual extraction of ZIP archive")
        
        ZipInputStream(file.inputStream()).use { zipStream ->
            var entry: ZipEntry?
            var extractedCount = 0
            val processedEntries = mutableSetOf<String>()

            while (true) {
                try {
                    entry = zipStream.nextEntry
                    if (entry == null) break
                } catch (e: java.util.zip.ZipException) {
                    Logger.warning("Skipping problematic ZIP entry: ${e.message}")
                    continue
                }

                val zipEntry = entry
                val entryName = zipEntry.name

                // Skip if already processed
                if (entryName in processedEntries) {
                    safeCloseEntry(zipStream)
                    continue
                }

                processedEntries.add(entryName)
                
                // Process the entry
                extractedCount += processManualEntry(zipEntry, zipStream, targetDir)
                
                safeCloseEntry(zipStream)
            }

            Logger.info("Extracted $extractedCount files using manual mode")

            if (extractedCount == 0) {
                throw RuntimeException("ZIP archive appears to be empty or corrupted")
            }
        }
    }

    /**
     * Processes a single ZIP entry during manual extraction.
     * 
     * @param zipEntry The ZIP entry to process
     * @param zipStream The ZIP input stream
     * @param targetDir The target directory
     * @return 1 if a file was extracted, 0 otherwise
     */
    private fun processManualEntry(zipEntry: ZipEntry, zipStream: ZipInputStream, targetDir: Path): Int {
        val entryName = zipEntry.name
        val entryPath = targetDir.resolve(entryName)

        // Check for path traversal
        if (!entryPath.startsWith(targetDir)) {
            Logger.warning("Path traversal attempt detected: $entryName")
            return 0
        }

        // Skip macOS system files
        if (entryName.contains("__MACOSX") || entryName.contains(".DS_Store")) {
            Logger.debug("Skipping macOS system file: $entryName")
            return 0
        }

        return if (zipEntry.isDirectory) {
            createDirectory(entryPath, entryName)
            0
        } else {
            extractFileManually(zipEntry, zipStream, entryPath)
        }
    }

    /**
     * Creates a directory during manual extraction.
     * 
     * @param entryPath The path to create
     * @param entryName The name of the entry for logging
     */
    private fun createDirectory(entryPath: Path, entryName: String) {
        try {
            Files.createDirectories(entryPath)
            Logger.debug("Created directory: $entryName")
        } catch (e: Exception) {
            Logger.warning("Failed to create directory $entryName: ${e.message}")
        }
    }

    /**
     * Extracts a file during manual extraction.
     * 
     * @param zipEntry The ZIP entry containing the file
     * @param zipStream The ZIP input stream
     * @param entryPath The path to extract the file to
     * @return 1 if the file was extracted successfully, 0 otherwise
     */
    private fun extractFileManually(zipEntry: ZipEntry, zipStream: ZipInputStream, entryPath: Path): Int {
        val entryName = zipEntry.name
        
        try {
            // Create parent directories if they don't exist
            Files.createDirectories(entryPath.parent)

            // Read content into a buffer and write to a file
            val buffer = ByteArray(8192)
            var bytesRead: Int

            Files.newOutputStream(entryPath).use { out ->
                while (zipStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
            }

            Logger.debug("Extracted file: $entryName (${Files.size(entryPath)} bytes)")

            // Set executable permissions for gradlew
            if (entryName.endsWith("gradlew")) {
                entryPath.toFile().setExecutable(true)
                Logger.debug("Set executable permissions for: $entryName")
            }
            
            return 1
        } catch (e: Exception) {
            Logger.warning("Failed to extract file $entryName: ${e.message}")
            return 0
        }
    }

    /**
     * Safely closes a ZIP entry, ignoring any exceptions.
     * 
     * @param zipStream The ZIP input stream
     */
    private fun safeCloseEntry(zipStream: ZipInputStream) {
        try {
            zipStream.closeEntry()
        } catch (_: Exception) {
        }
    }
}