package org.contentauth.c2paexample

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for C2PA library functionality
 * Equivalent to iOS C2PAExampleTests.swift
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class C2PATests {

    private val context = RuntimeEnvironment.getApplication()

    // MARK: - Core Library Tests

    @Test
    fun testLibraryVersion() = runBlocking {
        val result = runAllTests(context).find { it.name == "Library Version" }
        assertNotNull("Library Version test should exist", result)
        assertTrue("Library Version test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testErrorHandling() = runBlocking {
        val result = runAllTests(context).find { it.name == "Error Handling" }
        assertNotNull("Error Handling test should exist", result)
        assertTrue("Error Handling test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testReadImage() = runBlocking {
        val result = runAllTests(context).find { it.name == "Read Manifest from Test Image" }
        assertNotNull("Read Manifest test should exist", result)
        assertTrue("Read Manifest test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testStreamAPI() = runBlocking {
        val result = runAllTests(context).find { it.name == "Stream API" }
        assertNotNull("Stream API test should exist", result)
        assertTrue("Stream API test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testBuilderAPI() = runBlocking {
        val result = runAllTests(context).find { it.name == "Builder API" }
        assertNotNull("Builder API test should exist", result)
        assertTrue("Builder API test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testBuilderNoEmbed() = runBlocking {
        val result = runAllTests(context).find { it.name == "Builder No-Embed" }
        assertNotNull("Builder No-Embed test should exist", result)
        assertTrue("Builder No-Embed test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testReadIngredient() = runBlocking {
        val result = runAllTests(context).find { it.name == "Read Ingredient" }
        assertNotNull("Read Ingredient test should exist", result)
        assertTrue("Read Ingredient test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testInvalidFileHandling() = runBlocking {
        val result = runAllTests(context).find { it.name == "Invalid File Handling" }
        assertNotNull("Invalid File Handling test should exist", result)
        assertTrue("Invalid File Handling test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testResourceReading() = runBlocking {
        val result = runAllTests(context).find { it.name == "Resource Reading" }
        assertNotNull("Resource Reading test should exist", result)
        assertTrue("Resource Reading test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testBuilderRemoteURL() = runBlocking {
        val result = runAllTests(context).find { it.name == "Builder Remote URL" }
        assertNotNull("Builder Remote URL test should exist", result)
        assertTrue("Builder Remote URL test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testBuilderAddResource() = runBlocking {
        val result = runAllTests(context).find { it.name == "Builder Add Resource" }
        assertNotNull("Builder Add Resource test should exist", result)
        assertTrue("Builder Add Resource test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testBuilderAddIngredient() = runBlocking {
        val result = runAllTests(context).find { it.name == "Builder Add Ingredient" }
        assertNotNull("Builder Add Ingredient test should exist", result)
        assertTrue("Builder Add Ingredient test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testBuilderFromArchive() = runBlocking {
        val result = runAllTests(context).find { it.name == "Builder from Archive" }
        assertNotNull("Builder from Archive test should exist", result)
        assertTrue("Builder from Archive test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testReaderWithManifestData() = runBlocking {
        val result = runAllTests(context).find { it.name == "Reader with Manifest Data" }
        assertNotNull("Reader with Manifest Data test should exist", result)
        assertTrue("Reader with Manifest Data test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testSignerWithCallback() = runBlocking {
        val result = runAllTests(context).find { it.name == "Signer with Callback" }
        assertNotNull("Signer with Callback test should exist", result)
        assertTrue("Signer with Callback test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testFileOperationsWithDataDir() = runBlocking {
        val result = runAllTests(context).find { it.name == "File Operations with Data Directory" }
        assertNotNull("File Operations with Data Directory test should exist", result)
        assertTrue("File Operations with Data Directory test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testWriteOnlyStreams() = runBlocking {
        val result = runAllTests(context).find { it.name == "Write-Only Streams" }
        assertNotNull("Write-Only Streams test should exist", result)
        assertTrue("Write-Only Streams test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testCustomStreamCallbacks() = runBlocking {
        val result = runAllTests(context).find { it.name == "Custom Stream Callbacks" }
        assertNotNull("Custom Stream Callbacks test should exist", result)
        assertTrue("Custom Stream Callbacks test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testStreamFileOptions() = runBlocking {
        val result = runAllTests(context).find { it.name == "Stream File Options" }
        assertNotNull("Stream File Options test should exist", result)
        assertTrue("Stream File Options test should pass: ${result?.message}", result?.success == true)
    }

    // MARK: - Signing Tests

    @Test
    fun testWebServiceSignerCreation() = runBlocking {
        val result = runAllTests(context).find { it.name == "Web Service Real Signing & Verification" }
        assertNotNull("Web Service Signer test should exist", result)
        assertTrue("Web Service Signer test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testHardwareSignerCreation() = runBlocking {
        val result = runAllTests(context).find { it.name == "Hardware Signer Creation" }
        assertNotNull("Hardware Signer test should exist", result)
        assertTrue("Hardware Signer test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testStrongBoxSignerCreation() = runBlocking {
        val result = runAllTests(context).find { it.name == "StrongBox Signer Creation" }
        assertNotNull("StrongBox Signer test should exist", result)
        assertTrue("StrongBox Signer test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testSigningAlgorithmTests() = runBlocking {
        val result = runAllTests(context).find { it.name == "Signing Algorithm Tests" }
        assertNotNull("Signing Algorithm test should exist", result)
        assertTrue("Signing Algorithm test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testSignerReserveSize() = runBlocking {
        val result = runAllTests(context).find { it.name == "Signer Reserve Size" }
        assertNotNull("Signer Reserve Size test should exist", result)
        assertTrue("Signer Reserve Size test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testReaderResourceErrorHandling() = runBlocking {
        val result = runAllTests(context).find { it.name == "Reader Resource Error Handling" }
        assertNotNull("Reader Resource Error Handling test should exist", result)
        assertTrue("Reader Resource Error Handling test should pass: ${result?.message}", result?.success == true)
    }

    @Test
    fun testErrorEnumCoverage() = runBlocking {
        val result = runAllTests(context).find { it.name == "Error Enum Coverage" }
        assertNotNull("Error Enum Coverage test should exist", result)
        assertTrue("Error Enum Coverage test should pass: ${result?.message}", result?.success == true)
    }
}