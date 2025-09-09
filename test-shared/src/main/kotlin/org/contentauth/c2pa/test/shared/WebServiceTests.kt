package org.contentauth.c2pa.test.shared

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.contentauth.c2pa.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * WebServiceTests - Web service tests for signing server
 * 
 * This file contains extracted test methods that can be run individually.
 */
abstract class WebServiceTests : TestBase() {
    
    companion object {
        // Use 10.0.2.2 for Android emulator to access host's localhost
        private const val EMULATOR_SERVER_URL = "http://10.0.2.2:8080"
        
        private fun getServerUrl(): String {
            return EMULATOR_SERVER_URL
        }
        
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    private suspend fun isServerAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val healthClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url("${getServerUrl()}/health")
                .build()
                
            val response = healthClient.newCall(request).execute()
            response.isSuccessful.also { response.close() }
        } catch (e: Exception) {
            false
        }
    }
    
    // Individual test methods
    
    suspend fun testWebServiceSigningAndVerification(): TestResult = withContext(Dispatchers.IO) {
        if (!isServerAvailable()) {
            return@withContext TestResult(
                "Web Service Signing & Verification",
                true, // Mark as success but skipped
                "SKIPPED: Server not available",
                status = TestStatus.SKIPPED
            )
        }
        
        runTest("Web Service Signing & Verification") {
            try {
                // Create test image data
                val testImageData = loadResourceAsBytes("adobe_20220124_ci")
                
                // Create a signing request matching what the server expects
                val manifestJson = """
                    {
                        "claim_generator": "test_app/1.0",
                        "assertions": [
                            {"label": "c2pa.test", "data": {"test": true}}
                        ]
                    }
                """.trimIndent()
                
                val signingRequest = """
                    {
                        "manifestJSON": ${JSONObject.quote(manifestJson)},
                        "format": "image/jpeg"
                    }
                """.trimIndent()
                
                // Sign via web service
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("request", "request.json", signingRequest.toRequestBody("application/json".toMediaType()))
                    .addFormDataPart("image", "test.jpg", testImageData.toRequestBody("image/jpeg".toMediaType()))
                    .build()
                
                val request = Request.Builder()
                    .url("${getServerUrl()}/api/v1/c2pa/sign")
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val success = response.isSuccessful
                val responseBody = response.body?.bytes()
                val responseCode = response.code
                val responseMessage = response.message
                response.close()
                
                // Debug logging
                println("Web service response: $responseCode $responseMessage")
                if (!success) {
                    println("Response body: ${responseBody?.let { String(it) }}")
                }
                
                if (success && responseBody != null && responseBody.isNotEmpty()) {
                    // Verify the signed image has a manifest by reading from the response data
                    val manifest = try {
                        Reader.fromStream(
                            format = "image/jpeg",
                            stream = ByteArrayStream(responseBody)
                        ).use { reader ->
                            reader.json()
                        }
                    } catch (e: Exception) {
                        null
                    }
                    val hasManifest = manifest != null && manifest.isNotEmpty()
                    
                    TestResult(
                        "Web Service Signing & Verification",
                        hasManifest,
                        if (hasManifest) "Successfully signed via web service" else "Signed but no manifest found",
                        "Response size: ${responseBody.size} bytes"
                    )
                } else {
                    TestResult(
                        "Web Service Signing & Verification",
                        false,
                        "Web service signing failed",
                        "HTTP ${responseCode}: ${responseMessage}"
                    )
                }
            } catch (e: Exception) {
                TestResult(
                    "Web Service Signing & Verification",
                    false,
                    "Exception during web service signing",
                    e.toString()
                )
            }
        }
    }
    
    suspend fun testWebServiceSignerCreation(): TestResult = withContext(Dispatchers.IO) {
        if (!isServerAvailable()) {
            return@withContext TestResult(
                "Web Service Signer Creation",
                true, // Mark as success but skipped
                "SKIPPED: Server not available",
                status = TestStatus.SKIPPED
            )
        }
        
        runTest("Web Service Signer Creation") {
            try {
                // Load test image
                val imageData = loadResourceAsBytes("adobe_20220124_ci")
                
                // Create signing request
                val signingRequest = JSONObject().apply {
                    put("manifestJSON", JSONObject().apply {
                        put("claim_generator", "c2pa-android-test/1.0")
                        put("title", "Web Service Test")
                    }.toString())
                    put("format", "image/jpeg")
                }
                
                // Create multipart request
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "request",
                        "request.json",
                        signingRequest.toString().toRequestBody("application/json".toMediaType())
                    )
                    .addFormDataPart(
                        "image", 
                        "test.jpg",
                        imageData.toRequestBody("image/jpeg".toMediaType())
                    )
                    .build()
                
                val request = Request.Builder()
                    .url("${getServerUrl()}/api/v1/c2pa/sign")
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val responseData = response.body?.bytes()
                
                val success = response.isSuccessful && responseData != null && responseData.isNotEmpty()
                
                // If successful, try to read the manifest from the signed image
                var manifestFound = false
                if (success && responseData != null) {
                    try {
                        val manifest = Reader.fromStream(
                            format = "image/jpeg",
                            stream = ByteArrayStream(responseData)
                        ).use { reader ->
                            reader.json()
                        }
                        manifestFound = manifest != null && manifest.isNotEmpty()
                    } catch (e: Exception) {
                        // Failed to read manifest
                    }
                }
                
                TestResult(
                    "Web Service Signer Creation",
                    success && manifestFound,
                    if (success && manifestFound) {
                        "Successfully signed image via web service"
                    } else if (success) {
                        "Image signed but manifest not found"
                    } else {
                        "Failed to sign image: ${response.code}"
                    },
                    "Response size: ${responseData?.size ?: 0} bytes, Manifest found: $manifestFound"
                )
            } catch (e: Exception) {
                TestResult(
                    "Web Service Signer Creation",
                    false,
                    "Exception creating web service signer",
                    e.toString()
                )
            }
        }
    }
    
    suspend fun testCSRSigning(): TestResult = withContext(Dispatchers.IO) {
        if (!isServerAvailable()) {
            return@withContext TestResult(
                "CSR Signing",
                true, // Mark as success but skipped
                "SKIPPED: Server not available",
                status = TestStatus.SKIPPED
            )
        }
        
        runTest("CSR Signing") {
            // Skip this test for now as it requires HardwareSecurity which is Android-specific
            // and not available in the test-shared module
            TestResult(
                "CSR Signing",
                true,
                "SKIPPED: CSR signing requires Android-specific HardwareSecurity",
                status = TestStatus.SKIPPED
            )
        }
    }
}