package org.contentauth.c2pa.test.shared

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Base class for all test suites.
 * Provides common functionality for loading resources and running tests.
 * Extracted from TestSuiteCore to enable modular test organization.
 */
abstract class BaseTestSuite {
    
    enum class TestStatus {
        PASSED,
        FAILED,
        SKIPPED
    }
    
    data class TestResult(
        val name: String,
        val success: Boolean,
        val message: String,
        val details: String? = null,
        val status: TestStatus = if (success) TestStatus.PASSED else TestStatus.FAILED
    )
    
    companion object {
        /**
         * Load a test resource from the classpath (test-shared module resources).
         */
        fun loadSharedResourceAsBytes(resourceName: String): ByteArray? {
            return try {
                BaseTestSuite::class.java.classLoader?.getResourceAsStream(resourceName)?.use { 
                    it.readBytes() 
                }
            } catch (e: Exception) {
                null
            }
        }
        
        fun loadSharedResourceAsString(resourceName: String): String? {
            return try {
                BaseTestSuite::class.java.classLoader?.getResourceAsStream(resourceName)?.use { 
                    it.bufferedReader().readText()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // Abstract methods to be implemented by subclasses
    protected abstract fun getContext(): Context
    protected abstract fun loadResourceAsBytes(resourceName: String): ByteArray
    protected abstract fun loadResourceAsString(resourceName: String): String
    protected abstract fun copyResourceToFile(resourceName: String, fileName: String): File
    
    /**
     * Helper function to run a test with error handling
     */
    protected suspend fun <T> runTest(name: String, block: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                throw Exception("Test '$name' failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Helper function to create a simple JPEG thumbnail for testing
     */
    protected open fun createSimpleJPEGThumbnail(): ByteArray {
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46,
            0x00, 0x01,
            0x01, 0x01,
            0x00, 0x48,
            0x00, 0x48,
            0x00, 0x00,
            0xFF.toByte(), 0xD9.toByte()
        )
    }
}