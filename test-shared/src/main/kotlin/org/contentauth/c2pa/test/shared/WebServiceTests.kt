package org.contentauth.c2pa.test.shared

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.contentauth.c2pa.*
import org.json.JSONObject

/**
 * WebServiceTests - Web service tests for signing server
 *
 * This file contains extracted test methods that can be run individually.
 */
abstract class WebServiceTests : TestBase() {

    companion object {
        // Use 10.0.2.2 for Android emulator to access host's localhost
        private const val EMULATOR_SERVER_URL = "http://10.0.2.2:8080"
        private const val DEFAULT_BEARER_TOKEN = "test-12345"

        private fun getServerUrl(): String {
            return System.getenv("SIGNING_SERVER_URL") ?: EMULATOR_SERVER_URL
        }

        private fun getBearerToken(): String {
            return System.getenv("BEARER_TOKEN") ?: DEFAULT_BEARER_TOKEN
        }

        private val httpClient =
                OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build()
    }

    private suspend fun isServerAvailable(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val healthClient =
                            OkHttpClient.Builder()
                                    .connectTimeout(5, TimeUnit.SECONDS)
                                    .readTimeout(5, TimeUnit.SECONDS)
                                    .build()

                    val request = Request.Builder().url("${getServerUrl()}/health").build()

                    val response = healthClient.newCall(request).execute()
                    response.isSuccessful.also { response.close() }
                } catch (e: Exception) {
                    false
                }
            }

    // Individual test methods

    suspend fun testWebServiceSigningAndVerification(): TestResult =
            withContext(Dispatchers.IO) {
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
                        // Fetch configuration from signing server
                        val configRequest =
                                Request.Builder()
                                        .url("${getServerUrl()}/api/v1/c2pa/configuration")
                                        .addHeader("Authorization", "Bearer ${getBearerToken()}")
                                        .build()

                        val configResponse = httpClient.newCall(configRequest).execute()
                        if (!configResponse.isSuccessful) {
                            return@runTest TestResult(
                                    "Web Service Signing & Verification",
                                    false,
                                    "Failed to fetch configuration: ${configResponse.code}"
                            )
                        }

                        val configJson = JSONObject(configResponse.body?.string() ?: "{}")
                        val algorithm = configJson.getString("algorithm")
                        val timestampUrl = configJson.optString("timestamp_url", "")
                        val signingUrl = configJson.getString("signing_url")
                        val certificateChainBase64 = configJson.getString("certificate_chain")

                        // Decode the base64-encoded certificate chain
                        val certificateChain =
                                String(
                                        android.util.Base64.decode(
                                                certificateChainBase64,
                                                android.util.Base64.DEFAULT
                                        )
                                )

                        // Create sign callback for remote signing
                        val remoteSignCallback =
                                object : SignCallback {
                                    override fun sign(data: ByteArray): ByteArray {
                                        val requestBody =
                                                JSONObject()
                                                        .apply {
                                                            put(
                                                                    "dataToSign",
                                                                    android.util.Base64
                                                                            .encodeToString(
                                                                                    data,
                                                                                    android.util
                                                                                            .Base64
                                                                                            .NO_WRAP
                                                                            )
                                                            )
                                                        }
                                                        .toString()

                                        val request =
                                                okhttp3.Request.Builder()
                                                        .url(signingUrl)
                                                        .post(
                                                                requestBody.toRequestBody(
                                                                        "application/json".toMediaType()
                                                                )
                                                        )
                                                        .addHeader(
                                                                "Authorization",
                                                                "Bearer ${getBearerToken()}"
                                                        )
                                                        .build()

                                        val response = httpClient.newCall(request).execute()
                                        if (!response.isSuccessful) {
                                            throw Exception(
                                                    "Remote signing failed: ${response.code}"
                                            )
                                        }

                                        val responseJson =
                                                JSONObject(response.body?.string() ?: "{}")
                                        val signatureBase64 = responseJson.getString("signature")

                                        return android.util.Base64.decode(
                                                signatureBase64,
                                                android.util.Base64.NO_WRAP
                                        )
                                    }
                                }

                        // Create signer using callback
                        val signer =
                                Signer.withCallback(
                                        algorithm = SigningAlgorithm.valueOf(algorithm.uppercase()),
                                        certificateChainPEM = certificateChain,
                                        tsaURL = if (timestampUrl.isEmpty()) null else timestampUrl,
                                        sign = remoteSignCallback::sign
                                )

                        // Create test image and manifest
                        val testImageData = loadResourceAsBytes("adobe_20220124_ci")
                        val manifestJson =
                                """
                                {
                                    "claim_generator": "test_app/1.0",
                                    "assertions": [
                                        {"label": "c2pa.test", "data": {"test": true}}
                                    ]
                                }
                                """.trimIndent()

                        // Sign the image
                        val builder = Builder.fromJson(manifestJson)
                        val sourceStream = DataStream(testImageData)
                        val destStream = ByteArrayStream()

                        builder.sign("image/jpeg", sourceStream, destStream, signer)
                        val signedImageData = destStream.getData()

                        // Verify the signed image has a manifest
                        val manifest =
                                Reader.fromStream(
                                                format = "image/jpeg",
                                                stream = ByteArrayStream(signedImageData)
                                        )
                                        .use { reader -> reader.json() }
                        val hasManifest = manifest.isNotEmpty()

                        TestResult(
                                "Web Service Signing & Verification",
                                hasManifest,
                                if (hasManifest) "Successfully signed via web service"
                                else "Signed but no manifest found",
                                "Signed image size: ${signedImageData.size} bytes"
                        )
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

    suspend fun testWebServiceSignerCreation(): TestResult =
            withContext(Dispatchers.IO) {
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
                        // Fetch configuration from signing server
                        val configRequest =
                                Request.Builder()
                                        .url("${getServerUrl()}/api/v1/c2pa/configuration")
                                        .addHeader("Authorization", "Bearer ${getBearerToken()}")
                                        .build()

                        val configResponse = httpClient.newCall(configRequest).execute()
                        if (!configResponse.isSuccessful) {
                            return@runTest TestResult(
                                    "Web Service Signer Creation",
                                    false,
                                    "Failed to fetch configuration: ${configResponse.code}"
                            )
                        }

                        val configJson = JSONObject(configResponse.body?.string() ?: "{}")
                        val algorithm = configJson.getString("algorithm")
                        val timestampUrl = configJson.optString("timestamp_url", "")
                        val signingUrl = configJson.getString("signing_url")
                        val certificateChainBase64 = configJson.getString("certificate_chain")

                        // Decode the base64-encoded certificate chain
                        val certificateChain =
                                String(
                                        android.util.Base64.decode(
                                                certificateChainBase64,
                                                android.util.Base64.DEFAULT
                                        )
                                )

                        // Create sign callback for remote signing
                        val remoteSignCallback =
                                object : SignCallback {
                                    override fun sign(data: ByteArray): ByteArray {
                                        val requestBody =
                                                JSONObject()
                                                        .apply {
                                                            put(
                                                                    "dataToSign",
                                                                    android.util.Base64
                                                                            .encodeToString(
                                                                                    data,
                                                                                    android.util
                                                                                            .Base64
                                                                                            .NO_WRAP
                                                                            )
                                                            )
                                                        }
                                                        .toString()

                                        val request =
                                                okhttp3.Request.Builder()
                                                        .url(signingUrl)
                                                        .post(
                                                                requestBody.toRequestBody(
                                                                        "application/json".toMediaType()
                                                                )
                                                        )
                                                        .addHeader(
                                                                "Authorization",
                                                                "Bearer ${getBearerToken()}"
                                                        )
                                                        .build()

                                        val response = httpClient.newCall(request).execute()
                                        if (!response.isSuccessful) {
                                            throw Exception(
                                                    "Remote signing failed: ${response.code}"
                                            )
                                        }

                                        val responseJson =
                                                JSONObject(response.body?.string() ?: "{}")
                                        val signatureBase64 = responseJson.getString("signature")

                                        return android.util.Base64.decode(
                                                signatureBase64,
                                                android.util.Base64.NO_WRAP
                                        )
                                    }
                                }

                        // Create signer using callback
                        val signer =
                                Signer.withCallback(
                                        algorithm = SigningAlgorithm.valueOf(algorithm.uppercase()),
                                        certificateChainPEM = certificateChain,
                                        tsaURL = if (timestampUrl.isEmpty()) null else timestampUrl,
                                        sign = remoteSignCallback::sign
                                )

                        // Simple test: sign a small image
                        val testImageData = loadResourceAsBytes("adobe_20220124_ci")
                        val manifestJson =
                                """
                                {
                                    "claim_generator": "c2pa-android-test/1.0",
                                    "title": "Web Service Test"
                                }
                                """.trimIndent()

                        val builder = Builder.fromJson(manifestJson)
                        val sourceStream = DataStream(testImageData)
                        val destStream = ByteArrayStream()

                        builder.sign("image/jpeg", sourceStream, destStream, signer)
                        val signedImageData = destStream.getData()

                        // Verify the signed image has a manifest
                        val manifest =
                                Reader.fromStream(
                                                format = "image/jpeg",
                                                stream = ByteArrayStream(signedImageData)
                                        )
                                        .use { reader -> reader.json() }
                        val hasManifest = manifest.isNotEmpty()

                        TestResult(
                                "Web Service Signer Creation",
                                hasManifest,
                                if (hasManifest) "Successfully created and used web service signer"
                                else "Signer created but manifest not found",
                                "Signed image size: ${signedImageData.size} bytes"
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

    suspend fun testCSRSigning(): TestResult =
            withContext(Dispatchers.IO) {
                if (!isServerAvailable()) {
                    return@withContext TestResult(
                            "CSR Signing",
                            true, // Mark as success but skipped
                            "SKIPPED: Server not available",
                            status = TestStatus.SKIPPED
                    )
                }

                runTest("CSR Signing") {
                    // Skip this test for now as it requires HardwareSecurity which is
                    // Android-specific
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
