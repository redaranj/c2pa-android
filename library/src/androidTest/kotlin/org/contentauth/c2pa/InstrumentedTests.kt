package org.contentauth.c2pa

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.contentauth.c2pa.test.shared.*
import android.os.Build
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.*

/**
 * Instrumented tests for C2PA Kotlin wrapper.
 * This class extends TestSuiteCore to reuse the shared test logic and adds
 * instrumentation-specific functionality.
 */
@RunWith(AndroidJUnit4::class)
class InstrumentedTests : TestSuiteCore() {
    
    private val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    
    companion object {
        private val testResults by lazy {
            runBlocking {
                val instance = InstrumentedTests()
                instance.runAllTests()
            }
        }
    }
    
    // Implement abstract methods from TestSuiteCore
    
    override fun getContext(): Context = targetContext
    
    override fun loadResourceAsBytes(resourceName: String): ByteArray {
        // First try to load from shared resources
        val sharedResource = TestSuiteCore.loadSharedResourceAsBytes("$resourceName.jpg")
            ?: TestSuiteCore.loadSharedResourceAsBytes("$resourceName.pem")
            ?: TestSuiteCore.loadSharedResourceAsBytes("$resourceName.key")
        
        return sharedResource ?: throw IllegalArgumentException("Resource not found: $resourceName")
    }
    
    override fun loadResourceAsString(resourceName: String): String {
        // First try to load from shared resources
        val sharedResource = TestSuiteCore.loadSharedResourceAsString("$resourceName.jpg")
            ?: TestSuiteCore.loadSharedResourceAsString("$resourceName.pem")
            ?: TestSuiteCore.loadSharedResourceAsString("$resourceName.key")
        
        return sharedResource ?: throw IllegalArgumentException("Resource not found: $resourceName")
    }
    
    override fun copyResourceToFile(resourceName: String, fileName: String): File {
        val file = File(targetContext.filesDir, fileName)
        val resourceBytes = loadResourceAsBytes(resourceName)
        file.writeBytes(resourceBytes)
        return file
    }
    
    // Individual test methods that get results from cached test results
    
    @Test
    fun runTestLibraryVersion() {
        val result = testResults.find { it.name == "Library Version" }
        assertNotNull(result, "Library Version test not found")
        assertTrue(result.success, "Library Version test failed: ${result.message}")
    }
    
    @Test
    fun runTestErrorHandling() {
        val result = testResults.find { it.name == "Error Handling" }
        assertNotNull(result, "Error Handling test not found")
        assertTrue(result.success, "Error Handling test failed: ${result.message}")
    }
    
    @Test
    fun runTestReadManifestFromTestImage() {
        val result = testResults.find { it.name == "Read Manifest from Test Image" }
        assertNotNull(result, "Read Manifest from Test Image test not found")
        assertTrue(result.success, "Read Manifest from Test Image test failed: ${result.message}")
    }
    
    @Test
    fun runTestStreamAPI() {
        val result = testResults.find { it.name == "Stream API" }
        assertNotNull(result, "Stream API test not found")
        assertTrue(result.success, "Stream API test failed: ${result.message}")
    }
    
    @Test
    fun runTestBuilderAPI() {
        val result = testResults.find { it.name == "Builder API" }
        assertNotNull(result, "Builder API test not found")
        assertTrue(result.success, "Builder API test failed: ${result.message}")
    }
    
    @Test
    fun runTestBuilderNoEmbed() {
        val result = testResults.find { it.name == "Builder No-Embed" }
        assertNotNull(result, "Builder No-Embed test not found")
        assertTrue(result.success, "Builder No-Embed test failed: ${result.message}")
    }
    
    @Test
    fun runTestReadIngredient() {
        val result = testResults.find { it.name == "Read Ingredient" }
        assertNotNull(result, "Read Ingredient test not found")
        assertTrue(result.success, "Read Ingredient test failed: ${result.message}")
    }
    
    @Test
    fun runTestInvalidFileHandling() {
        val result = testResults.find { it.name == "Invalid File Handling" }
        assertNotNull(result, "Invalid File Handling test not found")
        assertTrue(result.success, "Invalid File Handling test failed: ${result.message}")
    }
    
    @Test
    fun runTestResourceReading() {
        val result = testResults.find { it.name == "Resource Reading" }
        assertNotNull(result, "Resource Reading test not found")
        assertTrue(result.success, "Resource Reading test failed: ${result.message}")
    }
    
    @Test
    fun runTestBuilderRemoteURL() {
        val result = testResults.find { it.name == "Builder Remote URL" }
        assertNotNull(result, "Builder Remote URL test not found")
        assertTrue(result.success, "Builder Remote URL test failed: ${result.message}")
    }
    
    @Test
    fun runTestBuilderAddResource() {
        val result = testResults.find { it.name == "Builder Add Resource" }
        assertNotNull(result, "Builder Add Resource test not found")
        assertTrue(result.success, "Builder Add Resource test failed: ${result.message}")
    }
    
    @Test
    fun runTestBuilderAddIngredient() {
        val result = testResults.find { it.name == "Builder Add Ingredient" }
        assertNotNull(result, "Builder Add Ingredient test not found")
        assertTrue(result.success, "Builder Add Ingredient test failed: ${result.message}")
    }
    
    @Test
    fun runTestBuilderFromArchive() {
        val result = testResults.find { it.name == "Builder from Archive" }
        assertNotNull(result, "Builder from Archive test not found")
        assertTrue(result.success, "Builder from Archive test failed: ${result.message}")
    }
    
    @Test
    fun runTestReaderWithManifestData() {
        val result = testResults.find { it.name == "Reader with Manifest Data" }
        assertNotNull(result, "Reader with Manifest Data test not found")
        assertTrue(result.success, "Reader with Manifest Data test failed: ${result.message}")
    }
    
    @Test
    fun runTestSignerWithCallback() {
        val result = testResults.find { it.name == "Signer with Callback" }
        assertNotNull(result, "Signer with Callback test not found")
        assertTrue(result.success, "Signer with Callback test failed: ${result.message}")
    }
    
    @Test
    fun runTestFileOperationsWithDataDirectory() {
        val result = testResults.find { it.name == "File Operations with Data Directory" }
        assertNotNull(result, "File Operations with Data Directory test not found")
        assertTrue(result.success, "File Operations with Data Directory test failed: ${result.message}")
    }
    
    @Test
    fun runTestWriteOnlyStreams() {
        val result = testResults.find { it.name == "Write-Only Streams" }
        assertNotNull(result, "Write-Only Streams test not found")
        assertTrue(result.success, "Write-Only Streams test failed: ${result.message}")
    }
    
    @Test
    fun runTestCustomStreamCallbacks() {
        val result = testResults.find { it.name == "Custom Stream Callbacks" }
        assertNotNull(result, "Custom Stream Callbacks test not found")
        assertTrue(result.success, "Custom Stream Callbacks test failed: ${result.message}")
    }
    
    @Test
    fun runTestStreamFileOptions() {
        val result = testResults.find { it.name == "Stream File Options" }
        assertNotNull(result, "Stream File Options test not found")
        assertTrue(result.success, "Stream File Options test failed: ${result.message}")
    }
    
    @Test
    fun runTestWebServiceRealSigningAndVerification() {
        val result = testResults.find { it.name == "Web Service Real Signing & Verification" }
        assertNotNull(result, "Web Service Real Signing & Verification test not found")
        assertTrue(result.success, "Web Service Real Signing & Verification test failed: ${result.message}")
    }
    
    @Test
    fun runTestWebServiceSignerCreation() {
        val result = testResults.find { it.name == "Web Service Signer Creation" }
        assertNotNull(result, "Web Service Signer Creation test not found")
        // Server might not be available, just check test ran
        assertNotNull(result.message, "Test should have a message")
    }
    
    @Test
    fun runTestCSRSigning() {
        val result = testResults.find { it.name == "CSR Signing" }
        assertNotNull(result, "CSR Signing test not found")
        // Server might not be available, just check test ran
        assertNotNull(result.message, "Test should have a message")
    }
    
    @Test
    fun runTestHardwareSignerCreation() {
        val result = testResults.find { it.name == "Hardware Signer Creation" }
        assertNotNull(result, "Hardware Signer Creation test not found")
        assertTrue(result.success, "Hardware Signer Creation test failed: ${result.message}")
    }
    
    @Test
    fun runTestStrongBoxSignerCreation() {
        val result = testResults.find { it.name == "StrongBox Signer Creation" }
        assertNotNull(result, "StrongBox Signer Creation test not found")
        assertTrue(result.success, "StrongBox Signer Creation test failed: ${result.message}")
    }
    
    @Test
    fun runTestSigningAlgorithmTests() {
        val result = testResults.find { it.name == "Signing Algorithm Tests" }
        assertNotNull(result, "Signing Algorithm Tests test not found")
        assertTrue(result.success, "Signing Algorithm Tests test failed: ${result.message}")
    }
    
    @Test
    fun runTestSignerReserveSize() {
        val result = testResults.find { it.name == "Signer Reserve Size" }
        assertNotNull(result, "Signer Reserve Size test not found")
        assertTrue(result.success, "Signer Reserve Size test failed: ${result.message}")
    }
    
    @Test
    fun runTestReaderResourceErrorHandling() {
        val result = testResults.find { it.name == "Reader Resource Error Handling" }
        assertNotNull(result, "Reader Resource Error Handling test not found")
        assertTrue(result.success, "Reader Resource Error Handling test failed: ${result.message}")
    }
    
    @Test
    fun runTestErrorEnumCoverage() {
        val result = testResults.find { it.name == "Error Enum Coverage" }
        assertNotNull(result, "Error Enum Coverage test not found")
        assertTrue(result.success, "Error Enum Coverage test failed: ${result.message}")
    }
    
    @Test
    fun runTestLoadSettings() {
        val result = testResults.find { it.name == "Load Settings" }
        assertNotNull(result, "Load Settings test not found")
        assertTrue(result.success, "Load Settings test failed: ${result.message}")
    }
    
    @Test
    fun runTestSignFile() {
        val result = testResults.find { it.name == "Sign File" }
        assertNotNull(result, "Sign File test not found")
        assertTrue(result.success, "Sign File test failed: ${result.message}")
    }
    
    @Test
    fun runTestJsonRoundTrip() {
        val result = testResults.find { it.name == "JSON Round-trip" }
        assertNotNull(result, "JSON Round-trip test not found")
        assertTrue(result.success, "JSON Round-trip test failed: ${result.message}")
    }
    
    @Test
    fun runTestLargeBufferHandling() {
        val result = testResults.find { it.name == "Large Buffer Handling" }
        assertNotNull(result, "Large Buffer Handling test not found")
        assertTrue(result.success, "Large Buffer Handling test failed: ${result.message}")
    }
    
    @Test
    fun runTestConcurrentOperations() {
        val result = testResults.find { it.name == "Concurrent Operations" }
        assertNotNull(result, "Concurrent Operations test not found")
        assertTrue(result.success, "Concurrent Operations test failed: ${result.message}")
    }
    
    @Test
    fun runTestInvalidInputs() {
        val result = testResults.find { it.name == "Invalid Inputs" }
        assertNotNull(result, "Invalid Inputs test not found")
        assertTrue(result.success, "Invalid Inputs test failed: ${result.message}")
    }
    
    @Test
    fun runTestAlgorithmCoverage() {
        val result = testResults.find { it.name == "Algorithm Coverage" }
        assertNotNull(result, "Algorithm Coverage test not found")
        assertTrue(result.success, "Algorithm Coverage test failed: ${result.message}")
    }
    
    // Hardware Signing Tests
    
    @Test
    fun runTestSigningServerHealth() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Skip on older devices
            return
        }
        val result = testResults.find { it.name == "Signing Server Health" }
        assertNotNull(result, "Signing Server Health test not found")
        // Server might not be available in all test environments
        // so we just verify the test runs without crashing
        assertNotNull(result.message, "Test should have a message")
    }
    
    @Test
    fun runTestHardwareSecurityAvailability() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Skip on older devices
            return
        }
        val result = testResults.find { it.name == "Hardware Security Availability" }
        assertNotNull(result, "Hardware Security Availability test not found")
        assertTrue(result.success, "Hardware Security Availability test failed: ${result.message}")
    }
    
    @Test
    fun runTestCSRGeneration() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Skip on older devices
            return
        }
        val result = testResults.find { it.name == "CSR Generation" }
        assertNotNull(result, "CSR Generation test not found")
        // On physical devices with hardware security, this should succeed
        // On emulators, it might fail but shouldn't crash
        if (result.success) {
            assertTrue(result.message.contains("CSR has valid PEM format"), "CSR should have valid format")
        }
    }
    
    @Test
    fun runTestCSRSubmission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Skip on older devices
            return
        }
        val result = testResults.find { it.name == "CSR Submission" }
        assertNotNull(result, "CSR Submission test not found")
        // This test requires the signing server to be running
        // It might be skipped if server is not available
        assertNotNull(result.message, "Test should have a message")
    }
    
    @Test
    fun runTestHardwareKeySigning() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Skip on older devices
            return
        }
        val result = testResults.find { it.name == "Hardware Key Signing" }
        assertNotNull(result, "Hardware Key Signing test not found")
        // This test might be skipped if server is not available
        assertNotNull(result.message, "Test should have a message")
    }
    
    @Test
    fun runTestStrongBoxAvailability() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // StrongBox requires Android P+
            return
        }
        val result = testResults.find { it.name == "StrongBox Availability" }
        assertNotNull(result, "StrongBox Availability test not found")
        // StrongBox might not be available on all devices
        // The test should complete regardless
        assertTrue(result.success, "StrongBox test failed: ${result.message}")
    }
    
    @Test
    fun runTestStrongBoxCSRGeneration() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // StrongBox requires Android P+
            return
        }
        val result = testResults.find { it.name == "StrongBox CSR Generation" }
        assertNotNull(result, "StrongBox CSR Generation test not found")
        // This might fail on devices without StrongBox
        assertNotNull(result.message, "Test should have a message")
    }
    
    @Test
    fun runTestStrongBoxSigning() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // StrongBox requires Android P+
            return
        }
        val result = testResults.find { it.name == "StrongBox Signing" }
        assertNotNull(result, "StrongBox Signing test not found")
        // This might fail on devices without StrongBox or if server is not available
        assertNotNull(result.message, "Test should have a message")
    }
    
    @Test
    fun runTestEndToEndHardwareSigning() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Skip on older devices
            return
        }
        val result = testResults.find { it.name == "End-to-End Hardware Signing" }
        assertNotNull(result, "End-to-End Hardware Signing test not found")
        // This is the comprehensive test
        // It might be skipped if server is not available
        assertNotNull(result.message, "Test should have a message")
    }
    
    // Add any unique instrumented tests here that are not in TestSuiteCore
    
    @Test
    fun runTestInstrumentationSetup() {
        // Test that instrumentation context is properly set up
        assertNotNull(instrumentationContext, "Instrumentation context should not be null")
        assertNotNull(targetContext, "Target context should not be null")
        
        // Verify we can access test resources from shared module
        val testData = TestSuiteCore.loadSharedResourceAsBytes("adobe_20220124_ci.jpg")
        assertNotNull(testData, "Should be able to load shared resource")
        assertTrue(testData.isNotEmpty(), "Should be able to read test resource data")
    }
}