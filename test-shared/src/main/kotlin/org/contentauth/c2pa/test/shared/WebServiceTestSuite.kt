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
 * Web Service tests for C2PA Android
 * Tests the signing server endpoints for C2PA signing and CSR signing
 */
class WebServiceTestSuite(private val context: Context) {
    
    companion object {
        // Use 10.0.2.2 for Android emulator to access host's localhost
        private const val EMULATOR_SERVER_URL = "http://10.0.2.2:8080"
        // Use localhost for physical devices via adb reverse
        private const val LOCALHOST_SERVER_URL = "http://localhost:8080"
        // Use Tailscale URL for physical devices on the same Tailscale network
        private const val TAILSCALE_SERVER_URL = "https://air.tiger-agama.ts.net:8081"
        
        private fun getServerUrl(): String {
            // Use Tailscale URL for now to bypass local network issues
            return TAILSCALE_SERVER_URL
        }
        
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .hostnameVerifier { _, _ -> true } // Accept any hostname for testing
            .apply {
                // Trust all certificates for testing (only for Tailscale self-signed cert)
                val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                })
                
                val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            }
            .build()
    }
    
    /**
     * Run all web service tests
     */
    suspend fun runAllTests(): List<TestSuiteCore.TestResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TestSuiteCore.TestResult>()
        
        // First check if server is reachable with a short timeout
        val serverAvailable = try {
            val healthClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .hostnameVerifier { _, _ -> true }
                .apply {
                    val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    })
                    
                    val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
                    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                    sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                }
                .build()
            
            val request = Request.Builder()
                .url("${getServerUrl()}/health")
                .build()
                
            val response = healthClient.newCall(request).execute()
            response.isSuccessful.also { response.close() }
        } catch (e: Exception) {
            println("[WebServiceTestSuite] Server not available at ${getServerUrl()}: ${e.message}")
            false
        }
        
        if (!serverAvailable) {
            // Skip all web service tests if server is not available
            results.add(TestSuiteCore.TestResult("Web Service Real Signing & Verification", false, "SKIPPED: Server not available"))
            results.add(TestSuiteCore.TestResult("Web Service Signer Creation", false, "SKIPPED: Server not available"))
            results.add(TestSuiteCore.TestResult("CSR Signing", false, "SKIPPED: Server not available"))
            return@withContext results
        }
        
        // Test C2PA signing via web service
        results.add(testWebServiceSignerCreation())
        
        // Test CSR signing via web service
        results.add(testCSRSigning())
        
        results
    }
    /**
     * Test web service signer creation and signing
     * This test mirrors iOS's testWebServiceSignerCreation
     */
    private suspend fun testWebServiceSignerCreation(): TestSuiteCore.TestResult {
        return try {
            val serverUrl = getServerUrl()
            println("[WebServiceTest] Using server URL: $serverUrl")
            
            // Load test image
            val imageData = TestSuiteCore.loadSharedResourceAsBytes("adobe_20220124_ci.jpg")
                ?: throw IllegalStateException("Cannot load test image")
            println("[WebServiceTest] Loaded image: ${imageData.size} bytes")
            
            // Create signing request matching iOS format
            val signingRequest = JSONObject().apply {
                put("manifestJSON", JSONObject().apply {
                    put("claim_generator", "c2pa-android-test/1.0")
                    put("title", "Web Service Test")
                }.toString())
                put("format", "image/jpeg")
            }
            
            // Create multipart request matching iOS format
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
                .url("$serverUrl/api/v1/c2pa/sign")
                .post(requestBody)
                .build()
            
            println("[WebServiceTest] Sending POST to: $serverUrl/api/v1/c2pa/sign")
            val response = httpClient.newCall(request).execute()
            println("[WebServiceTest] Response code: ${response.code}")
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
            
            TestSuiteCore.TestResult(
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
            println("[WebServiceTest] Exception: ${e.message}")
            e.printStackTrace()
            TestSuiteCore.TestResult(
                "Web Service Signer Creation",
                false,
                "Test failed with exception: ${e.message}",
                e.toString()
            )
        }
    }
    
    /**
     * Test CSR signing via web service
     * This test mirrors iOS's testSecureEnclaveCSRSigning
     */
    private suspend fun testCSRSigning(): TestSuiteCore.TestResult {
        return try {
            // For CSR signing, we need to generate a CSR first
            // This would typically come from hardware security module
            
            // Generate a test CSR (simplified for testing)
            val csrPem = """
                -----BEGIN CERTIFICATE REQUEST-----
                MIICijCCAXICAQAwRTELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMRIwEAYDVQQH
                DAlTYW4gRGllZ28xFTATBgNVBAoMDFRlc3QgQ29tcGFueTCCASIwDQYJKoZIhvcN
                AQEBBQADggEPADCCAQoCggEBAL1234567890abcdefghijklmnopqrstuvwxyz12
                34567890ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890abcdefghijklmnopqrst
                uvwxyz1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890abcdefghijkl
                mnopqrstuvwxyz1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890abcd
                efghijklmnopqrstuvwxyz1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ123456
                7890abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGHIJKLMNOPQRSTUVWX
                YZ1234567890abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGHIJKLMNOP
                QRSTUVWXYZ1234567890CAwEAAaAAMA0GCSqGSIb3DQEBCwUAA4IBAQCTestSig
                nature1234567890
                -----END CERTIFICATE REQUEST-----
            """.trimIndent()
            
            val serverUrl = getServerUrl()
            
            // Create request to sign CSR
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("csr", csrPem)
                .addFormDataPart("algorithm", "ES256")
                .build()
            
            val request = Request.Builder()
                .url("$serverUrl/api/v1/certificates/sign")
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            // Check if we got a certificate back
            val success = response.isSuccessful && 
                         responseBody.contains("BEGIN CERTIFICATE")
            
            TestSuiteCore.TestResult(
                "CSR Signing",
                success,
                if (success) {
                    "CSR signed successfully"
                } else {
                    "Failed to sign CSR: ${response.code}"
                },
                if (success) {
                    "Certificate chain received"
                } else {
                    responseBody.take(200)
                }
            )
        } catch (e: Exception) {
            TestSuiteCore.TestResult(
                "CSR Signing",
                false,
                "Test failed with exception",
                e.toString()
            )
        }
    }
}