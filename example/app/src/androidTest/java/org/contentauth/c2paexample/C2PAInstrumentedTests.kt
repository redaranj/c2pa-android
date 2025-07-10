package org.contentauth.c2paexample

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for C2PA library functionality.
 * These tests run on an Android device or emulator and use the same test suite
 * as the UI tests in C2PATestScreen.
 */
@RunWith(AndroidJUnit4::class)
class C2PAInstrumentedTests {
    
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testSuite = C2PATestSuite(context)
    
    @Test
    fun testLibraryVersion() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Library Version" }
        assertNotNull("Library Version test should exist", result)
        assertTrue("Library Version test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testErrorHandling() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Error Handling" }
        assertNotNull("Error Handling test should exist", result)
        assertTrue("Error Handling test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testReadManifestFromTestImage() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Read Manifest from Test Image" }
        assertNotNull("Read Manifest test should exist", result)
        assertTrue("Read Manifest test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testStreamAPI() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Stream API" }
        assertNotNull("Stream API test should exist", result)
        assertTrue("Stream API test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testBuilderAPI() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Builder API" }
        assertNotNull("Builder API test should exist", result)
        assertTrue("Builder API test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testBuilderNoEmbed() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Builder No-Embed" }
        assertNotNull("Builder No-Embed test should exist", result)
        assertTrue("Builder No-Embed test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testReadIngredient() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Read Ingredient" }
        assertNotNull("Read Ingredient test should exist", result)
        assertTrue("Read Ingredient test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testInvalidFileHandling() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Invalid File Handling" }
        assertNotNull("Invalid File Handling test should exist", result)
        assertTrue("Invalid File Handling test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testResourceReading() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Resource Reading" }
        assertNotNull("Resource Reading test should exist", result)
        assertTrue("Resource Reading test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testBuilderRemoteURL() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Builder Remote URL" }
        assertNotNull("Builder Remote URL test should exist", result)
        assertTrue("Builder Remote URL test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testBuilderAddResource() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Builder Add Resource" }
        assertNotNull("Builder Add Resource test should exist", result)
        assertTrue("Builder Add Resource test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testBuilderAddIngredient() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Builder Add Ingredient" }
        assertNotNull("Builder Add Ingredient test should exist", result)
        assertTrue("Builder Add Ingredient test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testBuilderFromArchive() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Builder from Archive" }
        assertNotNull("Builder from Archive test should exist", result)
        assertTrue("Builder from Archive test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testReaderWithManifestData() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Reader with Manifest Data" }
        assertNotNull("Reader with Manifest Data test should exist", result)
        assertTrue("Reader with Manifest Data test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testSignerWithCallback() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Signer with Callback" }
        assertNotNull("Signer with Callback test should exist", result)
        assertTrue("Signer with Callback test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testFileOperationsWithDataDirectory() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "File Operations with Data Directory" }
        assertNotNull("File Operations test should exist", result)
        assertTrue("File Operations test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testWriteOnlyStreams() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Write-Only Streams" }
        assertNotNull("Write-Only Streams test should exist", result)
        assertTrue("Write-Only Streams test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testCustomStreamCallbacks() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Custom Stream Callbacks" }
        assertNotNull("Custom Stream Callbacks test should exist", result)
        assertTrue("Custom Stream Callbacks test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testStreamFileOptions() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Stream File Options" }
        assertNotNull("Stream File Options test should exist", result)
        assertTrue("Stream File Options test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testWebServiceSigning() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Web Service Real Signing & Verification" }
        assertNotNull("Web Service Signing test should exist", result)
        assertTrue("Web Service Signing test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testHardwareSignerCreation() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Hardware Signer Creation" }
        assertNotNull("Hardware Signer test should exist", result)
        assertTrue("Hardware Signer test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testStrongBoxSignerCreation() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "StrongBox Signer Creation" }
        assertNotNull("StrongBox Signer test should exist", result)
        assertTrue("StrongBox Signer test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testSigningAlgorithms() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Signing Algorithm Tests" }
        assertNotNull("Signing Algorithm test should exist", result)
        assertTrue("Signing Algorithm test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testSignerReserveSize() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Signer Reserve Size" }
        assertNotNull("Signer Reserve Size test should exist", result)
        assertTrue("Signer Reserve Size test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testReaderResourceErrorHandling() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Reader Resource Error Handling" }
        assertNotNull("Reader Resource Error test should exist", result)
        assertTrue("Reader Resource Error test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testErrorEnumCoverage() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Error Enum Coverage" }
        assertNotNull("Error Enum Coverage test should exist", result)
        assertTrue("Error Enum Coverage test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testLoadSettings() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Load Settings" }
        assertNotNull("Load Settings test should exist", result)
        assertTrue("Load Settings test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testSignFile() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Sign File" }
        assertNotNull("Sign File test should exist", result)
        assertTrue("Sign File test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testJsonRoundTrip() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "JSON Round-trip" }
        assertNotNull("JSON Round-trip test should exist", result)
        assertTrue("JSON Round-trip test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testLargeBufferHandling() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Large Buffer Handling" }
        assertNotNull("Large Buffer Handling test should exist", result)
        assertTrue("Large Buffer Handling test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testConcurrentOperations() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Concurrent Operations" }
        assertNotNull("Concurrent Operations test should exist", result)
        assertTrue("Concurrent Operations test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testInvalidInputs() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Invalid Inputs" }
        assertNotNull("Invalid Inputs test should exist", result)
        assertTrue("Invalid Inputs test should pass: ${result?.message}", result?.success == true)
    }
    
    @Test
    fun testAlgorithmCoverage() = runBlocking {
        val results = testSuite.runAllTests()
        val result = results.find { it.name == "Algorithm Coverage" }
        assertNotNull("Algorithm Coverage test should exist", result)
        assertTrue("Algorithm Coverage test should pass: ${result?.message}", result?.success == true)
    }
    
}