package org.contentauth.c2pa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

/**
 * Client for communicating with the C2PA signing server
 * Compatible with both the iOS and Android signing server implementations
 */
class SigningServerClient(
    private val baseUrl: String = "http://localhost:8080",
    private val apiKey: String? = null
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Data models matching the server API
    
    @Serializable
    data class CertificateSigningRequest(
        val csr: String,
        val metadata: CSRMetadata? = null
    )
    
    @Serializable
    data class CSRMetadata(
        val deviceId: String? = null,
        val appVersion: String? = null,
        val purpose: String? = null
    )
    
    @Serializable
    data class SignedCertificateResponse(
        val certificateId: String,
        val certificateChain: String,
        val expiresAt: String, // ISO 8601 date string
        val serialNumber: String
    )
    
    @Serializable
    data class C2PASigningRequest(
        val manifestJSON: String,
        val format: String,
        val imageData: String? = null // Base64 encoded for JSON requests
    )
    
    @Serializable
    data class C2PASigningResponse(
        val manifestStore: String, // Base64 encoded
        val signatureInfo: SignatureInfo
    )
    
    @Serializable
    data class SignatureInfo(
        val algorithm: String,
        val certificateChain: String? = null,
        val timestamp: String // ISO 8601 date string
    )
    
    @Serializable
    data class ServerStatus(
        val status: String,
        val version: String,
        val mode: String,
        val c2pa_version: String
    )
    
    // Public API methods
    
    /**
     * Check if the signing server is available
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            apiKey?.let {
                connection.setRequestProperty("X-API-Key", it)
            }
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            responseCode == 200
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get server status information
     */
    suspend fun getStatus(): ServerStatus? = withContext(Dispatchers.IO) {
        try {
            val url = URL(baseUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            apiKey?.let {
                connection.setRequestProperty("X-API-Key", it)
            }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                json.decodeFromString<ServerStatus>(response)
            } else {
                connection.disconnect()
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Submit a Certificate Signing Request to the server
     */
    suspend fun signCSR(
        csr: String,
        metadata: CSRMetadata? = null
    ): Result<SignedCertificateResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/api/v1/certificates/sign")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            apiKey?.let {
                connection.setRequestProperty("X-API-Key", it)
            }
            
            // Prepare request body
            val request = CertificateSigningRequest(csr, metadata)
            val requestJson = json.encodeToString(request)
            
            // Send request
            connection.outputStream.use { output ->
                output.write(requestJson.toByteArray())
            }
            
            // Read response
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                
                val signedCert = json.decodeFromString<SignedCertificateResponse>(response)
                Result.success(signedCert)
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP ${connection.responseCode}"
                connection.disconnect()
                Result.failure(C2PAError.Api("CSR signing failed: $error"))
            }
        } catch (e: Exception) {
            Result.failure(C2PAError.Api("CSR signing request failed: ${e.message}"))
        }
    }
    
    /**
     * Sign a C2PA manifest with image data
     */
    suspend fun signManifest(
        manifestJSON: String,
        imageData: ByteArray,
        format: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            // For multipart requests, we'll use the multipart form data approach
            val url = URL("$baseUrl/api/v1/c2pa/sign")
            val connection = url.openConnection() as HttpURLConnection
            
            val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
            
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            apiKey?.let {
                connection.setRequestProperty("X-API-Key", it)
            }
            
            // Build multipart request
            val outputStream = ByteArrayOutputStream()
            val writer = outputStream.writer()
            
            // Add request JSON part
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"request\"\r\n")
            writer.append("Content-Type: application/json\r\n\r\n")
            writer.append(json.encodeToString(C2PASigningRequest(manifestJSON, format)))
            writer.append("\r\n")
            writer.flush()
            
            // Add image data part
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"\r\n")
            writer.append("Content-Type: $format\r\n\r\n")
            writer.flush()
            outputStream.write(imageData)
            writer.append("\r\n")
            
            // End boundary
            writer.append("--$boundary--\r\n")
            writer.flush()
            
            // Send request
            connection.outputStream.use { output ->
                output.write(outputStream.toByteArray())
            }
            
            // Read response
            if (connection.responseCode == 200) {
                val responseData = connection.inputStream.use { input ->
                    input.readBytes()
                }
                connection.disconnect()
                Result.success(responseData)
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP ${connection.responseCode}"
                connection.disconnect()
                Result.failure(C2PAError.Api("Manifest signing failed: $error"))
            }
        } catch (e: Exception) {
            Result.failure(C2PAError.Api("Manifest signing request failed: ${e.message}"))
        }
    }
    
    /**
     * Sign a C2PA manifest using JSON format (base64 encoded image)
     */
    suspend fun signManifestJSON(
        manifestJSON: String,
        imageData: ByteArray,
        format: String
    ): Result<C2PASigningResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/api/v1/c2pa/sign")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            apiKey?.let {
                connection.setRequestProperty("X-API-Key", it)
            }
            
            // Prepare request with base64 encoded image
            val imageBase64 = android.util.Base64.encodeToString(imageData, android.util.Base64.NO_WRAP)
            val request = mapOf(
                "manifestJSON" to manifestJSON,
                "format" to format,
                "imageData" to imageBase64
            )
            val requestJson = json.encodeToString(request)
            
            // Send request
            connection.outputStream.use { output ->
                output.write(requestJson.toByteArray())
            }
            
            // Read response
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                
                // Parse response which includes base64 encoded manifest store
                val signedResponse = json.decodeFromString<C2PASigningResponse>(response)
                Result.success(signedResponse)
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP ${connection.responseCode}"
                connection.disconnect()
                Result.failure(C2PAError.Api("Manifest signing failed: $error"))
            }
        } catch (e: Exception) {
            Result.failure(C2PAError.Api("Manifest signing request failed: ${e.message}"))
        }
    }
    
    companion object {
        // Default server URLs for different environments
        const val LOCAL_SERVER = "http://localhost:8080"
        const val EMULATOR_LOCAL_SERVER = "http://10.0.2.2:8080" // Android emulator localhost
        
        /**
         * Get the appropriate server URL based on the environment
         */
        fun getDefaultServerUrl(isEmulator: Boolean = false): String {
            return if (isEmulator) EMULATOR_LOCAL_SERVER else LOCAL_SERVER
        }
    }
}