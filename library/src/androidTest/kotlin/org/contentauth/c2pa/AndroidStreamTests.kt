package org.contentauth.c2pa

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.contentauth.c2pa.test.shared.StreamTests
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Android instrumented tests for stream operations.
 */
@RunWith(AndroidJUnit4::class)
class AndroidStreamTests : StreamTests() {
    
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    
    override fun getContext(): Context = targetContext
    
    override fun loadResourceAsBytes(resourceName: String): ByteArray {
        val sharedResource = loadSharedResourceAsBytes("$resourceName.jpg")
            ?: loadSharedResourceAsBytes("$resourceName.pem")
            ?: loadSharedResourceAsBytes("$resourceName.key")
        
        return sharedResource ?: throw IllegalArgumentException("Resource not found: $resourceName")
    }
    
    override fun loadResourceAsString(resourceName: String): String {
        val sharedResource = loadSharedResourceAsString("$resourceName.jpg")
            ?: loadSharedResourceAsString("$resourceName.pem")
            ?: loadSharedResourceAsString("$resourceName.key")
        
        return sharedResource ?: throw IllegalArgumentException("Resource not found: $resourceName")
    }
    
    override fun copyResourceToFile(resourceName: String, fileName: String): File {
        val file = File(targetContext.filesDir, fileName)
        val resourceBytes = loadResourceAsBytes(resourceName)
        file.writeBytes(resourceBytes)
        return file
    }
    
    // Individual test methods
    
    @Test
    fun runTestStreamOperations() = runBlocking {
        val result = testStreamOperations()
        assertTrue(result.success, "Stream Operations test failed: ${result.message}")
    }
    
    @Test
    fun runTestStreamFileOptions() = runBlocking {
        val result = testStreamFileOptions()
        assertTrue(result.success, "Stream File Options test failed: ${result.message}")
    }
    
    @Test
    fun runTestWriteOnlyStreams() = runBlocking {
        val result = testWriteOnlyStreams()
        assertTrue(result.success, "Write-Only Streams test failed: ${result.message}")
    }
    
    @Test
    fun runTestCustomStreamCallbacks() = runBlocking {
        val result = testCustomStreamCallbacks()
        assertTrue(result.success, "Custom Stream Callbacks test failed: ${result.message}")
    }
    
    @Test
    fun runTestFileOperationsWithDataDirectory() = runBlocking {
        val result = testFileOperationsWithDataDirectory()
        assertTrue(result.success, "File Operations with Data Directory test failed: ${result.message}")
    }
}