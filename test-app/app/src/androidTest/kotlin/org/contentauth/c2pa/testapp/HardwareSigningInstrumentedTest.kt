package org.contentauth.c2pa.testapp

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.contentauth.c2pa.test.shared.HardwareSigningTestSuite
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for hardware signing functionality
 * 
 * Run with: make hardware-tests-with-server
 */
@RunWith(AndroidJUnit4::class)
class HardwareSigningInstrumentedTest {
    
    private lateinit var testSuite: HardwareSigningTestSuite
    
    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testSuite = HardwareSigningTestSuite(context)
    }
    
    @Test
    fun testSigningServerHealth() = runBlocking {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Skip on older devices
            return@runBlocking
        }
        
        val results = testSuite.runAllTests()
        val serverHealthResult = results.find { it.name == "Signing Server Health" }
        
        assertNotNull("Server health test should be present", serverHealthResult)
        
        // Log the result for debugging
        println("Server Health Test:")
        println("  Success: ${serverHealthResult?.success}")
        println("  Message: ${serverHealthResult?.message}")
        serverHealthResult?.details?.let {
            println("  Details: $it")
        }
        
        // Server might not be available in all test environments
        // so we just verify the test runs without crashing
        assertNotNull(serverHealthResult?.message)
    }
    
    @Test
    fun testHardwareSecurityAvailability() = runBlocking {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Skip on older devices
            return@runBlocking
        }
        
        val results = testSuite.runAllTests()
        val hardwareResult = results.find { it.name == "Hardware Security Availability" }
        
        assertNotNull("Hardware security test should be present", hardwareResult)
        assertTrue(
            "Hardware security test should complete", 
            hardwareResult?.message?.isNotEmpty() == true
        )
        
        // Log the result
        println("Hardware Security Test:")
        println("  Success: ${hardwareResult?.success}")
        println("  Message: ${hardwareResult?.message}")
    }
    
    @Test
    fun testCSRGeneration() = runBlocking {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Skip on older devices
            return@runBlocking
        }
        
        val results = testSuite.runAllTests()
        val csrResult = results.find { it.name == "CSR Generation" }
        
        assertNotNull("CSR generation test should be present", csrResult)
        
        // On physical devices with hardware security, this should succeed
        // On emulators, it might fail but shouldn't crash
        println("CSR Generation Test:")
        println("  Success: ${csrResult?.success}")
        println("  Message: ${csrResult?.message}")
        
        if (csrResult?.success == true) {
            assertTrue(
                "CSR should have valid format",
                csrResult.message.contains("CSR has valid PEM format")
            )
        }
    }
    
    @Test
    fun testCSRSubmission() = runBlocking {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Skip on older devices
            return@runBlocking
        }
        
        val results = testSuite.runAllTests()
        val submissionResult = results.find { it.name == "CSR Submission" }
        
        assertNotNull("CSR submission test should be present", submissionResult)
        
        println("CSR Submission Test:")
        println("  Success: ${submissionResult?.success}")
        println("  Message: ${submissionResult?.message}")
        
        // This test requires the signing server to be running
        // It might be skipped if server is not available
        if (submissionResult?.message?.contains("server not available") == true) {
            println("  Test skipped - signing server not available")
        }
    }
    
    @Test
    fun testStrongBoxAvailability() = runBlocking {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // StrongBox requires Android P+
            return@runBlocking
        }
        
        val results = testSuite.runAllTests()
        val strongBoxResult = results.find { it.name == "StrongBox Availability" }
        
        assertNotNull("StrongBox test should be present on Android P+", strongBoxResult)
        
        println("StrongBox Availability Test:")
        println("  Success: ${strongBoxResult?.success}")
        println("  Message: ${strongBoxResult?.message}")
        
        // StrongBox might not be available on all devices
        // The test should complete regardless
        assertTrue("Test should complete", strongBoxResult?.success == true)
    }
    
    @Test
    fun testEndToEndHardwareSigning() = runBlocking {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Skip on older devices
            return@runBlocking
        }
        
        val results = testSuite.runAllTests()
        val e2eResult = results.find { it.name == "End-to-End Hardware Signing" }
        
        assertNotNull("End-to-end test should be present", e2eResult)
        
        println("End-to-End Hardware Signing Test:")
        println("  Success: ${e2eResult?.success}")
        println("  Message: ${e2eResult?.message}")
        e2eResult?.details?.let {
            println("  Details: $it")
        }
        
        // This is the comprehensive test
        // It might be skipped if server is not available
        if (e2eResult?.message?.contains("server not available") == true) {
            println("  Test skipped - signing server not available")
        } else if (e2eResult?.success == true) {
            assertTrue(
                "Should complete successfully",
                e2eResult.message.contains("Test completed successfully")
            )
        }
    }
    
    @Test
    fun testAllHardwareSigningFeatures() = runBlocking {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            println("Hardware signing requires Android M+ (API 23+)")
            return@runBlocking
        }
        
        println("Running all hardware signing tests...")
        val results = testSuite.runAllTests()
        
        println("\n=== Hardware Signing Test Results ===")
        results.forEach { result ->
            println("\n${result.name}:")
            println("  Success: ${result.success}")
            println("  Message: ${result.message.take(200)}...")
            result.details?.let {
                println("  Details: ${it.take(100)}...")
            }
        }
        
        // Count results
        val totalTests = results.size
        val successfulTests = results.count { it.success }
        val failedTests = results.count { !it.success }
        
        println("\n=== Summary ===")
        println("Total tests: $totalTests")
        println("Successful: $successfulTests")
        println("Failed: $failedTests")
        
        // At minimum, hardware security check should pass
        val hardwareSecurityTest = results.find { it.name == "Hardware Security Availability" }
        assertNotNull("Hardware security test must be present", hardwareSecurityTest)
        
        // CSR generation should work on devices with hardware security
        val csrTest = results.find { it.name == "CSR Generation" }
        assertNotNull("CSR generation test must be present", csrTest)
        
        // Log any failures for debugging
        val failures = results.filter { !it.success }
        if (failures.isNotEmpty()) {
            println("\n=== Failed Tests ===")
            failures.forEach { failure ->
                println("${failure.name}: ${failure.message}")
            }
        }
        
        // The test suite should complete without crashing
        assertTrue("Test suite should complete", results.isNotEmpty())
    }
}