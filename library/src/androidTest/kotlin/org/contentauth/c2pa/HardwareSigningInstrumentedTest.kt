package org.contentauth.c2pa

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
 * Library instrumented tests for hardware signing functionality
 * 
 * Run with: make tests-with-server
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
        if (csrResult?.success == true) {
            assertTrue(
                "CSR should have valid format",
                csrResult.message.contains("CSR has valid PEM format")
            )
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
        
        // StrongBox might not be available on all devices
        // The test should complete regardless
        assertTrue("Test should complete", strongBoxResult?.success == true)
    }
}