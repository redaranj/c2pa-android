/*
This file is licensed to you under the Apache License, Version 2.0
(http://www.apache.org/licenses/LICENSE-2.0) or the MIT license
(http://opensource.org/licenses/MIT), at your option.

Unless required by applicable law or agreed to in writing, this software is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS OF
ANY KIND, either express or implied. See the LICENSE-MIT and LICENSE-APACHE
files for the specific language governing permissions and limitations under
each license.
*/

package org.contentauth.c2pa.test.shared

import org.json.JSONObject
import java.io.File

/**
 * Utility class for running c2patool external validation on test files.
 *
 * c2patool is the official C2PA command-line tool that can validate and inspect
 * C2PA manifests. This class provides integration with c2patool for testing purposes.
 *
 * ## Usage
 *
 * ```kotlin
 * val runner = C2PAToolRunner("/path/to/.c2patool/c2patool")
 * if (runner.isAvailable()) {
 *     val result = runner.validate("/path/to/signed.jpg")
 *     if (result.isValid) {
 *         println("Manifest valid: ${result.manifestJson}")
 *     } else {
 *         println("Validation failed: ${result.error}")
 *     }
 * }
 * ```
 *
 * ## Setup
 *
 * Download c2patool using: `make c2patool-download`
 *
 * The tool will be installed at `.c2patool/c2patool` in the project root.
 */
class C2PAToolRunner(
    private val c2patoolPath: String = DEFAULT_C2PATOOL_PATH,
) {
    companion object {
        /** Default path to c2patool binary (relative to project root) */
        const val DEFAULT_C2PATOOL_PATH = ".c2patool/c2patool"

        /**
         * Attempts to find c2patool in common locations.
         *
         * @return A C2PAToolRunner if found, null otherwise.
         */
        fun findC2PATool(): C2PAToolRunner? {
            val paths = listOf(
                DEFAULT_C2PATOOL_PATH,
                System.getenv("C2PATOOL_PATH"),
                "/usr/local/bin/c2patool",
                "/opt/homebrew/bin/c2patool",
            ).filterNotNull()

            for (path in paths) {
                val file = File(path)
                if (file.exists() && file.canExecute()) {
                    return C2PAToolRunner(path)
                }
            }
            return null
        }
    }

    /**
     * Result of running c2patool validation.
     *
     * @property isValid Whether the manifest passed validation.
     * @property manifestJson The manifest JSON output (if successful).
     * @property error Error message (if validation failed).
     * @property exitCode The process exit code.
     * @property stdout The raw stdout output.
     * @property stderr The raw stderr output.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val manifestJson: String? = null,
        val error: String? = null,
        val exitCode: Int = 0,
        val stdout: String = "",
        val stderr: String = "",
    ) {
        /**
         * Parses the manifest JSON into a JSONObject.
         *
         * @return The parsed JSON or null if invalid.
         */
        fun parseManifestJson(): JSONObject? = manifestJson?.let {
            try {
                JSONObject(it)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Checks if the manifest contains a specific assertion label.
         *
         * @param label The assertion label to search for.
         * @return True if found in the manifest output.
         */
        fun hasAssertion(label: String): Boolean {
            return manifestJson?.contains(label) == true
        }

        /**
         * Checks if the manifest contains created assertions.
         *
         * @return True if created_assertions is present.
         */
        fun hasCreatedAssertions(): Boolean {
            return manifestJson?.contains("created_assertions") == true ||
                manifestJson?.contains("assertions") == true
        }

        /**
         * Checks if the manifest contains gathered assertions.
         *
         * @return True if gathered_assertions is present.
         */
        fun hasGatheredAssertions(): Boolean {
            return manifestJson?.contains("gathered_assertions") == true
        }
    }

    /**
     * Checks if c2patool is available and executable.
     *
     * @return True if c2patool can be executed.
     */
    fun isAvailable(): Boolean {
        val file = File(c2patoolPath)
        if (!file.exists() || !file.canExecute()) {
            return false
        }

        return try {
            val process = ProcessBuilder(c2patoolPath, "--version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the c2patool version.
     *
     * @return The version string or null if unavailable.
     */
    fun getVersion(): String? {
        if (!isAvailable()) return null

        return try {
            val process = ProcessBuilder(c2patoolPath, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validates a file with c2patool.
     *
     * @param filePath Path to the file to validate.
     * @param detailed Whether to use detailed output mode.
     * @return The validation result.
     */
    fun validate(filePath: String, detailed: Boolean = true): ValidationResult {
        if (!isAvailable()) {
            return ValidationResult(
                isValid = false,
                error = "c2patool not available at $c2patoolPath",
            )
        }

        val file = File(filePath)
        if (!file.exists()) {
            return ValidationResult(
                isValid = false,
                error = "File not found: $filePath",
            )
        }

        return try {
            val args = mutableListOf(c2patoolPath, filePath)
            if (detailed) {
                args.add("--detailed")
            }

            val process = ProcessBuilder(args)
                .start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                ValidationResult(
                    isValid = true,
                    manifestJson = stdout,
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr,
                )
            } else {
                ValidationResult(
                    isValid = false,
                    error = stderr.ifEmpty { "c2patool exited with code $exitCode" },
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr,
                )
            }
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                error = "Failed to run c2patool: ${e.message}",
            )
        }
    }

    /**
     * Extracts manifest JSON from a file without validation.
     *
     * @param filePath Path to the file.
     * @return The manifest JSON or null if extraction failed.
     */
    fun extractManifest(filePath: String): String? {
        val result = validate(filePath, detailed = true)
        return if (result.isValid) result.manifestJson else null
    }

    /**
     * Validates multiple files and returns results.
     *
     * @param filePaths List of file paths to validate.
     * @return Map of file path to validation result.
     */
    fun validateMultiple(filePaths: List<String>): Map<String, ValidationResult> {
        return filePaths.associateWith { validate(it) }
    }

    /**
     * Compares manifest contents between two files.
     *
     * @param file1 First file path.
     * @param file2 Second file path.
     * @return A comparison result describing differences.
     */
    fun compareManifests(file1: String, file2: String): ManifestComparison {
        val result1 = validate(file1)
        val result2 = validate(file2)

        if (!result1.isValid || !result2.isValid) {
            return ManifestComparison(
                identical = false,
                error = buildString {
                    if (!result1.isValid) append("File 1 invalid: ${result1.error}\n")
                    if (!result2.isValid) append("File 2 invalid: ${result2.error}")
                },
            )
        }

        val json1 = result1.parseManifestJson()
        val json2 = result2.parseManifestJson()

        if (json1 == null || json2 == null) {
            return ManifestComparison(
                identical = false,
                error = "Failed to parse manifest JSON",
            )
        }

        // Simple comparison - in production you'd want deeper comparison
        val identical = result1.manifestJson == result2.manifestJson

        return ManifestComparison(
            identical = identical,
            manifest1 = result1.manifestJson,
            manifest2 = result2.manifestJson,
        )
    }

    /**
     * Result of comparing two manifests.
     */
    data class ManifestComparison(
        val identical: Boolean,
        val error: String? = null,
        val manifest1: String? = null,
        val manifest2: String? = null,
        val differences: List<String> = emptyList(),
    )
}
