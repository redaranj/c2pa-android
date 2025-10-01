package org.contentauth.c2pa

import android.annotation.SuppressLint
import java.io.File
import java.security.KeyStore
import java.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/**
 * Load native C2PA libraries.
 * Safe to call multiple times - System.loadLibrary handles duplicates.
 */
@SuppressLint("UnsafeDynamicallyLoadedCode") // Intentional for server-specific library loading
internal fun loadC2PALibraries() {
    try {
        // Detect if we're running on Android or JVM
        val isAndroid = try {
            Class.forName("android.os.Build")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        
        if (isAndroid) {
            // Android: Load from APK
            System.loadLibrary("c2pa_c")
            System.loadLibrary("c2pa_jni")
        } else {
            // JVM (signing server): Load from file system using system properties
            val c2paServerLib = System.getProperty("c2pa.server.lib.path") 
                ?: throw UnsatisfiedLinkError("c2pa.server.lib.path system property not set")
            val c2paServerJni = System.getProperty("c2pa.server.jni.path")
                ?: throw UnsatisfiedLinkError("c2pa.server.jni.path system property not set")
            
            System.load(c2paServerLib)
            System.load(c2paServerJni)
        }
    } catch (e: UnsatisfiedLinkError) {
        // Libraries might already be loaded, ignore
    }
}

/**
 * Execute a C2PA operation with standard error handling
 */
internal inline fun <T : Any> executeC2PAOperation(
    errorMessage: String,
    operation: () -> T?
): T {
    return try {
        operation() ?: throw C2PAError.Api(C2PA.getError() ?: errorMessage)
    } catch (e: IllegalArgumentException) {
        throw C2PAError.Api(e.message ?: "Invalid arguments")
    } catch (e: RuntimeException) {
        val error = C2PA.getError()
        if (error != null) {
            throw C2PAError.Api(error)
        }
        throw C2PAError.Api(e.message ?: "Runtime error")
    }
}

/**
 * C2PA version fetched once
 */
val c2paVersion: String by lazy {
    C2PA.version()
}

/**
 * Web Service Helpers - OkHttp based
 */
object WebServiceHelpers {
    
    private val defaultClient = OkHttpClient()
    
    /**
     * Create a simple POST signer for binary data
     */
    fun basicPOSTSigner(
        url: String,
        authToken: String? = null,
        contentType: String = "application/octet-stream",
        client: OkHttpClient = defaultClient
    ): WebServiceSigner = { data ->
        val request = Request.Builder()
            .url(url)
            .post(data.toRequestBody(contentType.toMediaType()))
            .apply { authToken?.let { header("Authorization", it) } }
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw C2PAError.Api("HTTP ${response.code}: ${response.message}")
            }
            response.body?.bytes() ?: throw C2PAError.Api("Empty response body")
        }
    }
    
    /**
     * Create a JSON POST signer
     */
    fun jsonSigner(
        url: String,
        authToken: String? = null,
        additionalFields: Map<String, Any> = emptyMap(),
        responseField: String = "signature",
        client: OkHttpClient = defaultClient
    ): WebServiceSigner = { data ->
        val json = JSONObject().apply {
            additionalFields.forEach { (k, v) -> put(k, v) }
            put("data", Base64.getEncoder().encodeToString(data))
        }
        
        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .apply { authToken?.let { header("Authorization", it) } }
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw C2PAError.Api("HTTP ${response.code}: ${response.message}")
            }
            
            val responseBody = response.body?.string() 
                ?: throw C2PAError.Api("Empty response body")
            
            val responseJson = JSONObject(responseBody)
            val signatureBase64 = responseJson.optString(responseField)
                ?: throw C2PAError.Api("Missing '$responseField' in response")
            
            Base64.getDecoder().decode(signatureBase64)
        }
    }
}


/**
 * Helper Extensions
 */
fun exportPublicKeyPEM(fromKeychainTag: String): String {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    
    val certificate = keyStore.getCertificate(fromKeychainTag)
        ?: throw C2PAError.Api("Certificate not found for alias: $fromKeychainTag")
    
    val publicKeyBytes = certificate.publicKey.encoded
    val base64 = Base64.getEncoder().encodeToString(publicKeyBytes)
    
    return buildString {
        appendLine("-----BEGIN PUBLIC KEY-----")
        base64.chunked(64).forEach { appendLine(it) }
        append("-----END PUBLIC KEY-----")
    }
}

/**
 * Format utilities (additional Android utilities)
 */
object FormatUtils {
    /**
     * Convert a binary c2pa manifest into an embeddable version for the given format
     */
    @Throws(C2PAError::class)
    fun formatEmbeddable(format: String, manifestBytes: ByteArray): ByteArray {
        return executeC2PAOperation("Failed to format embeddable") {
            formatEmbeddableNative(format, manifestBytes)
        }
    }

    @JvmStatic
    private external fun formatEmbeddableNative(format: String, manifestBytes: ByteArray): ByteArray?
}