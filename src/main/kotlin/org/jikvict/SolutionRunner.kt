package org.jikvict

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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

    Logger.info("Processing zip files: ${arguments.firstZipFilePath} and ${arguments.secondZipFilePath} with timeout: ${arguments.timeoutSeconds} seconds")

    val solutionRunner = SolutionRunner()
    solutionRunner.executeCode(arguments.firstZipFile, arguments.secondZipFile, arguments.timeoutSeconds, arguments.resultsPath)
}

/**
 * Displays usage information for the application.
 */
private fun displayUsage() {
    Logger.info("Usage: SolutionRunner <first-zip-file-path> <second-zip-file-path> [timeout-seconds]")
    Logger.info("  <first-zip-file-path>: Path to the first zip archive containing part of the Gradle project")
    Logger.info("  <second-zip-file-path>: Path to the second zip archive containing part of the Gradle project")
    Logger.info("  [timeout-seconds]: Optional timeout in seconds (default: 300)")
}

/**
 * Parses command line arguments and validates them.
 *
 * @param args Command line arguments
 * @return Arguments object or null if arguments are invalid
 */
private fun parseArguments(args: Array<String>): Arguments? {
    if (args.size < 2) {
        return null
    }

    val firstZipFilePath = args[0]
    val secondZipFilePath = args[1]
    val timeoutSeconds = if (args.size > 2) args[2].toLongOrNull() ?: 300L else 300L
    val resultsPath = if (args.size > 3) args[3] else "jikvict-results.json"

    val firstZipFile = File(firstZipFilePath)
    if (!firstZipFile.exists()) {
        Logger.error("File not found: $firstZipFilePath")
        return null
    }

    val secondZipFile = File(secondZipFilePath)
    if (!secondZipFile.exists()) {
        Logger.error("File not found: $secondZipFilePath")
        return null
    }

    return Arguments(firstZipFilePath, firstZipFile, secondZipFilePath, secondZipFile, timeoutSeconds, resultsPath)
}

/**
 * Data class to hold parsed command line arguments.
 */
data class Arguments(
    val firstZipFilePath: String,
    val firstZipFile: File,
    val secondZipFilePath: String,
    val secondZipFile: File,
    val timeoutSeconds: Long,
    val resultsPath: String
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
 * Main class responsible for executing code from zip files.
 * Orchestrates the extraction, project discovery, and execution process.
 */
class SolutionRunner {

    /**
     * Executes code from two zip files with the specified timeout.
     * Both zip files are extracted to the same directory to merge their contents.
     *
     * @param firstFile First zip file containing part of the code to execute
     * @param secondFile Second zip file containing part of the code to execute
     * @param timeoutSeconds Timeout in seconds for execution
     */
    fun executeCode(firstFile: File, secondFile: File, timeoutSeconds: Long, resultPath: String) {
        val executionId = UUID.randomUUID().toString()
        val tempDir = Files.createTempDirectory("code-$executionId")

        try {
            // Extract and process the first zip file
            val zipExtractor = ZipExtractor()
            Logger.info("Extracting first zip file to: $tempDir")
            zipExtractor.extract(firstFile, tempDir)

            // Extract and process the second zip file to the same directory
            Logger.info("Extracting second zip file to: $tempDir")
            zipExtractor.extract(secondFile, tempDir)

            // Display the merged directory structure
            Logger.info("Merged directory structure:")
            DirectoryUtils.logTreeStructure(tempDir)

            // Find the project directory
            val projectDir = findProjectDirectory(tempDir) ?: throw ProjectNotFoundException()
            Logger.info("Found project directory: $projectDir")

            // Make gradlew executable with enhanced permissions
            val gradlewFile = projectDir.resolve("gradlew")
            val gradlewFileObj = gradlewFile.toFile()

            // Try multiple approaches to ensure executable permissions
            val permissionSet = gradlewFileObj.setExecutable(true, false) // executable for all users
            Logger.info("Setting executable permission for gradlew: ${if (permissionSet) "success" else "failed"}")

            // Also try setting read permissions explicitly
            gradlewFileObj.setReadable(true, false)

            // As a fallback, try using chmod command
            try {
                val chmodProcess = ProcessBuilder("chmod", "+x", gradlewFile.toString())
                    .redirectErrorStream(true)
                    .start()
                val chmodExitCode = chmodProcess.waitFor()
                Logger.info("Chmod command result: ${if (chmodExitCode == 0) "success" else "failed with code $chmodExitCode"}")

                // Verify permissions after chmod
                Logger.info("Gradlew file exists: ${Files.exists(gradlewFile)}")
                Logger.info("Gradlew file permissions: ${Files.getPosixFilePermissions(gradlewFile)}")
            } catch (e: Exception) {
                Logger.warning("Failed to execute chmod command: ${e.message}")
            }

            // Run the Gradle task
            val gradleRunner = GradleRunner()
            gradleRunner.runGradleTask(projectDir, timeoutSeconds, resultPath)
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
    fun runGradleTask(projectDir: Path, timeoutSeconds: Long, resultPath: String) {
        val relativeProjectPath = projectDir.toString()
        Logger.info("Relative project path: $relativeProjectPath")

        // Verify gradlew file exists and has proper permissions
        val gradlewFile = projectDir.resolve("gradlew")
        Logger.info("Gradlew file exists: ${Files.exists(gradlewFile)}")
        try {
            Logger.info("Gradlew file permissions: ${Files.getPosixFilePermissions(gradlewFile)}")
        } catch (e: Exception) {
            Logger.warning("Unable to get POSIX permissions: ${e.message}")
        }

        // First try: Direct execution of system Gradle
        try {
            Logger.info("Attempting to execute system Gradle directly...")
            val processBuilder = ProcessBuilder()
                .directory(projectDir.toFile())
                .command(
                    "gradle",
                    "runJIkvictTests",
                    "--no-daemon",
                    "--console=plain",
                    "-g",
                    System.getenv("GRADLE_USER_HOME") ?: "/gradle-cache"
                )
                .redirectErrorStream(true)

            Logger.info("Executing command: gradle test --no-daemon --console=plain -g ${System.getenv("GRADLE_USER_HOME") ?: "/gradle-cache"}")
            val process = processBuilder.start()

            // Capture output
            val output = process.inputStream.bufferedReader().use { it.readText() }

            // Wait for the process to complete with timeout
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!completed) {
                handleTimeout(process, timeoutSeconds, output)
                return
            } else {
                handleCompletion(process.exitValue(), output, projectDir, resultPath)
                return
            }
        } catch (e: Exception) {
            Logger.warning("Direct execution of system Gradle failed: ${e.message}")
            Logger.info("Trying alternative execution method with explicit path...")
        }

        // Second try: Using system Gradle with explicit path
        try {
            val processBuilder = ProcessBuilder()
                .directory(projectDir.toFile())
                .command(
                    "/opt/gradle/bin/gradle",
                    "runJIkvictTests",
                    "--no-daemon",
                    "--console=plain",
                    "-g",
                    System.getenv("GRADLE_USER_HOME") ?: "/gradle-cache"
                )
                .redirectErrorStream(true)

            Logger.info("Executing command: /opt/gradle/bin/gradle test --no-daemon --console=plain -g ${System.getenv("GRADLE_USER_HOME") ?: "/gradle-cache"}")
            val process = processBuilder.start()

            // Capture output
            val output = process.inputStream.bufferedReader().use { it.readText() }

            // Wait for the process to complete with timeout
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!completed) {
                handleTimeout(process, timeoutSeconds, output)
            } else {
                handleCompletion(process.exitValue(), output, projectDir, resultPath)
            }
        } catch (e: Exception) {
            Logger.error("Both system Gradle execution methods failed. Last error: ${e.message}")
            e.printStackTrace()
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
     * @param projectDir The project directory
     */
    private fun handleCompletion(exitCode: Int, output: String, projectDir: Path, resultsPath: String) {
        if (exitCode == 0) {
            Logger.info("Code executed successfully. Exit code: 0")

            val resultsFile = projectDir.resolve("build/jikvict-results.json")
            if (Files.exists(resultsFile)) {
                try {
                    val jsonContent = Files.readString(resultsFile)
                    Logger.info("Contents of jikvict-results.json:")
                    println(jsonContent)

                    val outputFile = Path.of(resultsPath)
                    Files.copy(resultsFile, outputFile, StandardCopyOption.REPLACE_EXISTING)
                    Logger.info("Results copied to: $outputFile")

                } catch (e: Exception) {
                    Logger.error("Failed to read or copy jikvict-results.json: ${e.message}")
                }
            } else {
                Logger.warning("jikvict-results.json file not found at: $resultsFile")
            }
        } else {
            Logger.error("Code execution failed. Exit code: $exitCode")
        }
        Logger.info("Execution logs:\n$output")
    }

}

/**
 * Class responsible for extracting ZIP archives.
 */
open class ZipExtractor {
    /**
     * Common root directories to strip from ZIP entries to ensure proper merging.
     * These are common patterns found in ZIP files that should be removed.
     */
    private val commonRootDirs = listOf(
        "default-structure/",
        "task1/default-structure/"
    )

    /**
     * Directories and files to be filtered out during extraction.
     */
    private val filteredPaths = listOf(
        "build/", "target/", ".idea/",
        "__MACOSX/", ".DS_Store",
        "Thumbs.db", ".git/"
    )

    /**
     * Checks if a path should be filtered out during extraction.
     *
     * @param path The path to check
     * @return true if the path should be filtered out, false otherwise
     */
    private fun shouldFilterPath(path: String): Boolean {
        return filteredPaths.any { filter ->
            path.contains(filter, ignoreCase = true)
        }
    }

    /**
     * Extracts a ZIP file to the target directory.
     *
     * @param file ZIP file to extract
     * @param targetDir Target directory to extract to
     */
    fun extract(file: File, targetDir: Path) {
        Logger.info("Starting extraction of ZIP file: ${file.name} to directory: $targetDir")

        try {
            // Check if the target directory exists and create it if it doesn't
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir)
                Logger.info("Created target directory: $targetDir")
            }

            extractZipFile(file, targetDir)
            Logger.info("ZIP archive ${file.name} successfully extracted to $targetDir")

            // Log the number of files extracted
            val extractedCount = countExtractedFiles(targetDir)
            Logger.info("Total files in target directory after extraction: $extractedCount")
        } catch (e: java.util.zip.ZipException) {
            Logger.warning("ZIP exception occurred during extraction of ${file.name}: ${e.message}")
            handleZipException(e, file, targetDir)
        } catch (e: Exception) {
            Logger.error("Unexpected error during ZIP extraction of ${file.name}: ${e.message}")
            throw RuntimeException("Failed to extract ZIP archive ${file.name}: ${e.message}", e)
        }
    }

    /**
     * Strips common root directories from entry paths to ensure proper merging.
     *
     * @param entryName The original entry name from the ZIP file
     * @return The entry name with common root directories stripped
     */
    private fun stripCommonRootDirs(entryName: String): String {
        var result = entryName

        // Skip filtered paths
        if (shouldFilterPath(entryName)) {
            return entryName
        }

        // Try to strip each common root directory
        for (rootDir in commonRootDirs) {
            if (result.startsWith(rootDir)) {
                result = result.substring(rootDir.length)
                Logger.debug("Stripped prefix '$rootDir' from entry: $entryName -> $result")
                break
            }
        }

        return result
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
        // Skip filtered paths
        if (shouldFilterPath(zipEntry.name)) {
            Logger.debug("Skipping filtered path: ${zipEntry.name}")
            return
        }

        // Strip common root directories to ensure proper merging
        val strippedEntryName = stripCommonRootDirs(zipEntry.name)
        val entryPath = targetDir.resolve(strippedEntryName)

        // Check for path traversal
        if (!entryPath.startsWith(targetDir)) {
            Logger.warning("Path traversal attempt detected: ${zipEntry.name}")
            return
        }

        if (zipEntry.isDirectory) {
            Files.createDirectories(entryPath)
            Logger.debug("Created directory: $strippedEntryName (original: ${zipEntry.name})")
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
        if (zipEntry.name.endsWith("gradlew") || zipEntry.name.endsWith("gradlew.bat") || zipEntry.name.endsWith("gradlew.sh")) {
            val gradlewExecutable = entryPath.toFile()
            val permissionSet = gradlewExecutable.setExecutable(true, false) // executable for all users
            gradlewExecutable.setReadable(true, false)
            Logger.debug("Set executable permissions for: ${zipEntry.name} - ${if (permissionSet) "success" else "failed"}")

            // As a fallback, try using chmod command
            try {
                val chmodProcess = ProcessBuilder("chmod", "+x", entryPath.toString())
                    .redirectErrorStream(true)
                    .start()
                val chmodExitCode = chmodProcess.waitFor()
                Logger.debug("Chmod command for ${zipEntry.name}: ${if (chmodExitCode == 0) "success" else "failed with code $chmodExitCode"}")
            } catch (e: Exception) {
                Logger.warning("Failed to execute chmod command for ${zipEntry.name}: ${e.message}")
            }
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
            // First, extract to a temporary directory to inspect the structure
            val tempExtractDir = Files.createTempDirectory("unzip-temp")

            try {
                // Extract to the temporary directory first
                val processBuilder =
                    ProcessBuilder("unzip", "-q", "-o", file.absolutePath, "-d", tempExtractDir.toString())
                        .redirectErrorStream(true)

                val process = processBuilder.start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    Logger.warning("Unzip command exited with code $exitCode. Output: $output")
                    return false
                }

                // Now move files from the temp directory to the target directory, stripping prefixes
                Logger.info("Reorganizing extracted files to strip common prefixes")

                // Check for common root directories in the extracted files
                Files.list(tempExtractDir).use { paths ->
                    paths.filter { Files.isDirectory(it) }
                        .map { it.fileName.toString() }
                        .toList()
                }

                // If we found "default-structure" or "task1" directories, we need to reorganize
                val defaultStructureDir = tempExtractDir.resolve("default-structure")
                val task1Dir = tempExtractDir.resolve("task1")

                if (Files.exists(defaultStructureDir)) {
                    // Move contents of default-structure to target directory
                    Logger.info("Moving contents from default-structure directory to target")
                    moveDirectoryContents(defaultStructureDir, targetDir)
                } else if (Files.exists(task1Dir) && Files.exists(task1Dir.resolve("default-structure"))) {
                    // Move contents of task1/default-structure to target directory
                    Logger.info("Moving contents from task1/default-structure directory to target")
                    moveDirectoryContents(task1Dir.resolve("default-structure"), targetDir)
                } else {
                    // No common prefixes found, just move everything
                    Logger.info("No common prefixes found, moving all files")
                    moveDirectoryContents(tempExtractDir, targetDir)
                }

                // Count and report extracted files
                val extractedCount = countExtractedFiles(targetDir)
                Logger.info("Extracted and reorganized $extractedCount files using unzip")

                // Set executable permissions for gradlew if it exists
                setGradlewExecutable(targetDir)
                return true
            } finally {
                // Clean up the temporary directory
                try {
                    DirectoryUtils.cleanupDirectory(tempExtractDir)
                } catch (e: Exception) {
                    Logger.warning("Failed to clean up temporary directory: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.warning("Error using unzip command: ${e.message}")
            return false
        }
    }

    /**
     * Moves the contents of a directory to another directory.
     *
     * @param sourceDir The source directory
     * @param targetDir The target directory
     */
    private fun moveDirectoryContents(sourceDir: Path, targetDir: Path) {
        Files.walk(sourceDir)
            .filter { it != sourceDir } // Skip the source directory itself
            .forEach { sourcePath ->
                val relativePath = sourceDir.relativize(sourcePath)
                val relativePathStr = relativePath.toString()

                // Skip filtered paths
                if (shouldFilterPath(relativePathStr)) {
                    Logger.debug("Skipping filtered path: $relativePathStr")
                    return@forEach
                }

                val targetPath = targetDir.resolve(relativePath)

                if (Files.isDirectory(sourcePath)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath)
                        Logger.debug("Created directory: $relativePath")
                    }
                } else {
                    // Create parent directories if they don't exist
                    Files.createDirectories(targetPath.parent)

                    // Move the file
                    try {
                        Files.move(sourcePath, targetPath)
                        Logger.debug("Moved file: $relativePath")
                    } catch (_: java.nio.file.FileAlreadyExistsException) {
                        // If the file already exists, replace it
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
                        Logger.debug("Replaced existing file: $relativePath")
                    }
                }
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
            .forEach { gradlewPath ->
                val gradlewFile = gradlewPath.toFile()
                val permissionSet = gradlewFile.setExecutable(true, false) // executable for all users
                gradlewFile.setReadable(true, false)
                Logger.debug("Set executable permissions for: ${gradlewPath.fileName} - ${if (permissionSet) "success" else "failed"}")

                // As a fallback, try using chmod command
                try {
                    val chmodProcess = ProcessBuilder("chmod", "+x", gradlewPath.toString())
                        .redirectErrorStream(true)
                        .start()
                    val chmodExitCode = chmodProcess.waitFor()
                    Logger.debug("Chmod command for ${gradlewPath.fileName}: ${if (chmodExitCode == 0) "success" else "failed with code $chmodExitCode"}")
                } catch (e: Exception) {
                    Logger.warning("Failed to execute chmod command for ${gradlewPath.fileName}: ${e.message}")
                }
            }
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

        // Skip filtered paths
        if (shouldFilterPath(entryName)) {
            Logger.debug("Skipping filtered path: $entryName")
            return 0
        }

        // Strip common root directories to ensure proper merging
        val strippedEntryName = stripCommonRootDirs(entryName)
        val entryPath = targetDir.resolve(strippedEntryName)

        // Check for path traversal
        if (!entryPath.startsWith(targetDir)) {
            Logger.warning("Path traversal attempt detected: $entryName")
            return 0
        }

        return if (zipEntry.isDirectory) {
            createDirectory(entryPath, strippedEntryName)
            Logger.debug("Created directory: $strippedEntryName (original: $entryName)")
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
        val originalEntryName = zipEntry.name
        val strippedEntryName = stripCommonRootDirs(originalEntryName)

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

            Logger.debug("Extracted file: $strippedEntryName (original: $originalEntryName) (${Files.size(entryPath)} bytes)")

            // Set executable permissions for gradlew
            if (strippedEntryName.endsWith("gradlew") || strippedEntryName.endsWith("gradlew.bat") || strippedEntryName.endsWith(
                    "gradlew.sh"
                )
            ) {
                val gradlewFile = entryPath.toFile()
                val permissionSet = gradlewFile.setExecutable(true, false) // executable for all users
                gradlewFile.setReadable(true, false)
                Logger.debug("Set executable permissions for: $strippedEntryName - ${if (permissionSet) "success" else "failed"}")

                // As a fallback, try using chmod command
                try {
                    val chmodProcess = ProcessBuilder("chmod", "+x", entryPath.toString())
                        .redirectErrorStream(true)
                        .start()
                    val chmodExitCode = chmodProcess.waitFor()
                    Logger.debug("Chmod command for $strippedEntryName: ${if (chmodExitCode == 0) "success" else "failed with code $chmodExitCode"}")
                } catch (e: Exception) {
                    Logger.warning("Failed to execute chmod command for $strippedEntryName: ${e.message}")
                }
            }

            return 1
        } catch (e: Exception) {
            Logger.warning("Failed to extract file $strippedEntryName (original: $originalEntryName): ${e.message}")
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