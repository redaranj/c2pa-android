package org.contentauth.c2pa.exampleapp.data

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.contentauth.c2pa.*
import org.contentauth.c2pa.exampleapp.model.SigningMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import javax.security.auth.x500.X500Principal

class C2PAManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "C2PAManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS_PREFIX = "C2PA_KEY_"
        private const val DEFAULT_TSA_URL = "http://timestamp.digicert.com"
    }
    
    private val httpClient = OkHttpClient()
    
    private var defaultCertificate: String? = null
    private var defaultPrivateKey: String? = null
    
    init {
        loadDefaultCertificates()
    }
    
    private fun loadDefaultCertificates() {
        try {
            // Load default test certificates from assets
            context.assets.open("default_certs.pem").use { stream ->
                defaultCertificate = stream.bufferedReader().readText()
            }
            context.assets.open("default_private.key").use { stream ->
                defaultPrivateKey = stream.bufferedReader().readText()
            }
            Log.d(TAG, "Default certificates loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading default certificates", e)
        }
    }
    
    suspend fun signImage(
        bitmap: Bitmap,
        location: Location? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            // Convert bitmap to JPEG bytes
            val imageBytes = ByteArrayOutputStream().use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.toByteArray()
            }
            
            Log.d(TAG, "Original image size: ${imageBytes.size} bytes")
            
            // Get current signing mode
            val signingMode = preferencesManager.signingMode.first()
            Log.d(TAG, "Using signing mode: $signingMode")
            
            // Create manifest JSON
            val manifestJSON = createManifestJSON(location)
            
            // Create appropriate signer based on mode
            val signer = createSigner(signingMode)
            
            // Sign the image using C2PA library
            val signedBytes = signImageData(imageBytes, manifestJSON, signer)
            
            Log.d(TAG, "Signed image size: ${signedBytes.size} bytes")
            Log.d(TAG, "Size difference: ${signedBytes.size - imageBytes.size} bytes")
            
            // Verify the signed image
            verifySignedImage(signedBytes)
            
            Result.success(signedBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error signing image", e)
            Result.failure(e)
        }
    }
    
    private suspend fun createSigner(mode: SigningMode): Signer {
        return when (mode) {
            SigningMode.DEFAULT -> createDefaultSigner()
            SigningMode.KEYSTORE -> createKeystoreSigner()
            SigningMode.HARDWARE -> createHardwareSigner()
            SigningMode.CUSTOM -> createCustomSigner()
            SigningMode.REMOTE -> createRemoteSigner()
        }
    }
    
    private fun createDefaultSigner(): Signer {
        requireNotNull(defaultCertificate) { "Default certificate not available" }
        requireNotNull(defaultPrivateKey) { "Default private key not available" }
        
        Log.d(TAG, "Creating default signer with test certificates")
        
        return Signer.fromKeys(
            certsPEM = defaultCertificate!!,
            privateKeyPEM = defaultPrivateKey!!,
            algorithm = SigningAlgorithm.ES256,
            tsaURL = DEFAULT_TSA_URL
        )
    }
    
    private suspend fun createKeystoreSigner(): Signer {
        val alias = "$KEYSTORE_ALIAS_PREFIX${SigningMode.KEYSTORE.name}"
        
        // Get or create keystore key
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        if (!keyStore.containsAlias(alias)) {
            Log.d(TAG, "Creating new Android Keystore key")
            createKeystoreKey(alias, false)
        }
        
        // Get certificate chain
        val certChain = getOrCreateKeystoreCertificate(alias)
        
        Log.d(TAG, "Creating Android Keystore signer")
        
        // Create a sign callback that uses the keystore key
        val signCallback = createKeystoreSignCallback(alias)
        
        return Signer.withCallback(
            algorithm = SigningAlgorithm.ES256,
            certificateChainPEM = certChain,
            tsaURL = DEFAULT_TSA_URL,
            sign = signCallback::sign
        )
    }
    
    private suspend fun createHardwareSigner(): Signer {
        val alias = preferencesManager.hardwareKeyAlias.first() 
            ?: "$KEYSTORE_ALIAS_PREFIX${SigningMode.HARDWARE.name}"
        
        // Get or create hardware-backed key
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        if (!keyStore.containsAlias(alias)) {
            Log.d(TAG, "Creating new hardware-backed key")
            createKeystoreKey(alias, true) // true for hardware-backed
            preferencesManager.setHardwareKeyAlias(alias)
        }
        
        // Get certificate chain from signing server
        val certChain = enrollHardwareKeyCertificate(alias)
        
        Log.d(TAG, "Creating hardware security signer")
        
        // Create a sign callback that uses the hardware key
        val signCallback = createKeystoreSignCallback(alias)
        
        return Signer.withCallback(
            algorithm = SigningAlgorithm.ES256,
            certificateChainPEM = certChain,
            tsaURL = DEFAULT_TSA_URL,
            sign = signCallback::sign
        )
    }
    
    private suspend fun createCustomSigner(): Signer {
        val certPEM = preferencesManager.customCertificate.first()
            ?: throw IllegalStateException("Custom certificate not configured")
        val keyPEM = preferencesManager.customPrivateKey.first()
            ?: throw IllegalStateException("Custom private key not configured")
        
        Log.d(TAG, "Creating custom signer with uploaded certificates")
        
        return Signer.fromKeys(
            certsPEM = certPEM,
            privateKeyPEM = keyPEM,
            algorithm = SigningAlgorithm.ES256,
            tsaURL = DEFAULT_TSA_URL
        )
    }
    
    private suspend fun createRemoteSigner(): Signer {
        val remoteUrl = preferencesManager.remoteUrl.first()
            ?: throw IllegalStateException("Remote signing URL not configured")
        val bearerToken = preferencesManager.remoteToken.first()
        
        val configUrl = if (remoteUrl.contains("/api/v1/c2pa/configuration")) {
            remoteUrl
        } else {
            "$remoteUrl/api/v1/c2pa/configuration"
        }
        
        Log.d(TAG, "Fetching configuration from: $configUrl")
        
        // Fetch configuration from remote service
        val request = Request.Builder()
            .url(configUrl)
            .apply {
                if (!bearerToken.isNullOrEmpty()) {
                    addHeader("Authorization", "Bearer $bearerToken")
                }
            }
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch remote configuration: ${response.code}")
        }
        
        val configJson = JSONObject(response.body?.string() ?: "{}")
        val algorithm = configJson.getString("algorithm")
        val timestampUrl = configJson.getString("timestamp_url")
        val signingUrl = configJson.getString("signing_url")
        val certificateChain = configJson.getString("certificate_chain")
        
        Log.d(TAG, "Creating remote signer with algorithm: $algorithm")
        
        // Create sign callback for remote signing
        val remoteSignCallback = createRemoteSignCallback(signingUrl, bearerToken)
        
        return Signer.withCallback(
            algorithm = SigningAlgorithm.valueOf(algorithm.uppercase()),
            certificateChainPEM = certificateChain,
            tsaURL = timestampUrl,
            sign = remoteSignCallback::sign
        )
    }
    
    private fun createKeystoreKey(alias: String, useHardware: Boolean) {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        
        val paramSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setDigests(KeyProperties.DIGEST_SHA256)
            setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            
            if (useHardware) {
                // Request hardware backing (StrongBox if available, TEE otherwise)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(true)
                }
            }
            
            // Self-signed certificate validity
            setCertificateSubject(X500Principal("CN=C2PA Android User, O=C2PA Example, C=US"))
            setCertificateSerialNumber(java.math.BigInteger.valueOf(System.currentTimeMillis()))
            setCertificateNotBefore(Date())
            setCertificateNotAfter(Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000))
        }.build()
        
        keyPairGenerator.initialize(paramSpec)
        keyPairGenerator.generateKeyPair()
    }
    
    private suspend fun getOrCreateKeystoreCertificate(alias: String): String {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        // Get the self-signed certificate from keystore
        val cert = keyStore.getCertificate(alias) as X509Certificate
        
        // Convert to PEM format
        val certPEM = buildString {
            append("-----BEGIN CERTIFICATE-----\n")
            append(Base64.encodeToString(cert.encoded, Base64.NO_WRAP))
            append("\n-----END CERTIFICATE-----\n")
        }
        
        return certPEM
    }
    
    private suspend fun enrollHardwareKeyCertificate(alias: String): String {
        val remoteUrl = preferencesManager.remoteUrl.first()
            ?: throw IllegalStateException("Remote URL required for hardware signing")
        val bearerToken = preferencesManager.remoteToken.first()
        
        // Generate CSR for the hardware key
        val csr = generateCSR(alias)
        
        // Submit CSR to signing server
        val enrollUrl = "$remoteUrl/api/v1/certificates/sign"
        
        val requestBody = JSONObject().apply {
            put("csr", csr)
            put("metadata", JSONObject().apply {
                put("device_id", getDeviceId())
                put("app_version", getAppVersion())
            })
        }.toString()
        
        val request = Request.Builder()
            .url(enrollUrl)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .apply {
                if (!bearerToken.isNullOrEmpty()) {
                    addHeader("Authorization", "Bearer $bearerToken")
                }
            }
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Certificate enrollment failed: ${response.code}")
        }
        
        val responseJson = JSONObject(response.body?.string() ?: "{}")
        val certChain = responseJson.getString("cert_chain")
        val certId = responseJson.getString("cert_id")
        
        Log.d(TAG, "Certificate enrolled successfully. ID: $certId")
        
        return certChain
    }
    
    private fun generateCSR(alias: String): String {
        // In a real implementation, this would generate a proper CSR
        // For now, returning a placeholder
        return "-----BEGIN CERTIFICATE REQUEST-----\n...\n-----END CERTIFICATE REQUEST-----"
    }
    
    private fun createKeystoreSignCallback(alias: String): SignCallback {
        return object : SignCallback {
            override fun sign(data: ByteArray): ByteArray {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
                keyStore.load(null)
                
                val privateKey = keyStore.getKey(alias, null) as PrivateKey
                
                val signature = java.security.Signature.getInstance("SHA256withECDSA")
                signature.initSign(privateKey)
                signature.update(data)
                
                return signature.sign()
            }
        }
    }
    
    private fun createRemoteSignCallback(signingUrl: String, bearerToken: String?): SignCallback {
        return object : SignCallback {
            override fun sign(data: ByteArray): ByteArray {
                val requestBody = JSONObject().apply {
                    put("dataToSign", Base64.encodeToString(data, Base64.NO_WRAP))
                }.toString()
                
                val request = Request.Builder()
                    .url(signingUrl)
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .apply {
                        if (!bearerToken.isNullOrEmpty()) {
                            addHeader("Authorization", "Bearer $bearerToken")
                        }
                    }
                    .build()
                
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("Remote signing failed: ${response.code}")
                }
                
                val responseJson = JSONObject(response.body?.string() ?: "{}")
                val signatureBase64 = responseJson.getString("signature")
                
                return Base64.decode(signatureBase64, Base64.NO_WRAP)
            }
        }
    }
    
    private fun signImageData(
        imageData: ByteArray,
        manifestJSON: String,
        signer: Signer
    ): ByteArray {
        // Create Builder with manifest
        val builder = Builder.fromJson(manifestJSON)
        
        // Create streams
        val sourceStream = DataStream(imageData)
        val outputStream = ByteArrayOutputStream()
        val destStream = CallbackStream(
            reader = null,
            seeker = { _, _ -> -1L },
            writer = { buffer, count ->
                outputStream.write(buffer, 0, count)
                count
            },
            flusher = {
                outputStream.flush()
                0
            }
        )
        
        // Sign the image
        builder.sign(
            format = "image/jpeg",
            source = sourceStream,
            dest = destStream,
            signer = signer
        )
        
        return outputStream.toByteArray()
    }
    
    private fun createManifestJSON(location: Location?): String {
        val manifest = JSONObject().apply {
            put("claim_generator", "C2PA Android Example/1.0.0")
            put("title", "Image signed on Android")
            
            val assertions = JSONArray()
            
            // Add location assertion if available
            location?.let {
                val locationAssertion = JSONObject().apply {
                    put("label", "stds.exif")
                    put("data", JSONObject().apply {
                        put("exif:GPSLatitude", it.latitude)
                        put("exif:GPSLongitude", it.longitude)
                        put("exif:GPSAltitude", it.altitude)
                        put("exif:GPSTimeStamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(Date(it.time)))
                    })
                }
                assertions.put(locationAssertion)
            }
            
            // Add creation time assertion
            val creationAssertion = JSONObject().apply {
                put("label", "c2pa.actions")
                put("data", JSONObject().apply {
                    put("actions", JSONArray().apply {
                        put(JSONObject().apply {
                            put("action", "c2pa.created")
                            put("when", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }.format(Date()))
                        })
                    })
                })
            }
            assertions.put(creationAssertion)
            
            put("assertions", assertions)
        }
        
        return manifest.toString()
    }
    
    private fun verifySignedImage(imageData: ByteArray) {
        try {
            // Create a temporary file for verification
            val tempFile = File.createTempFile("verify", ".jpg", context.cacheDir)
            tempFile.writeBytes(imageData)
            
            // Read and verify using C2PA
            val manifestJSON = C2PA.readFile(tempFile.absolutePath, null)
            
            Log.d(TAG, "✅ C2PA VERIFICATION SUCCESS")
            Log.d(TAG, "Manifest JSON length: ${manifestJSON.length} characters")
            
            // Parse and log key information
            val manifest = JSONObject(manifestJSON)
            manifest.optJSONObject("active_manifest")?.let { activeManifest ->
                Log.d(TAG, "Active manifest found")
                activeManifest.optString("claim_generator")?.let {
                    Log.d(TAG, "Claim generator: $it")
                }
                activeManifest.optString("title")?.let {
                    Log.d(TAG, "Title: $it")
                }
                activeManifest.optJSONObject("signature_info")?.let { sigInfo ->
                    Log.d(TAG, "Signature info present")
                    sigInfo.optString("alg")?.let { Log.d(TAG, "Algorithm: $it") }
                    sigInfo.optString("issuer")?.let { Log.d(TAG, "Issuer: $it") }
                }
            }
            
            // Clean up temp file
            tempFile.delete()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ C2PA VERIFICATION FAILED", e)
        }
    }
    
    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    
    fun saveImageToGallery(imageData: ByteArray): Result<String> {
        return try {
            // Implementation depends on Android version
            // For simplicity, saving to app's external files directory
            val photosDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "C2PA")
            if (!photosDir.exists()) {
                photosDir.mkdirs()
            }
            
            val fileName = "C2PA_${System.currentTimeMillis()}.jpg"
            val file = File(photosDir, fileName)
            file.writeBytes(imageData)
            
            Log.d(TAG, "Image saved to: ${file.absolutePath}")
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image", e)
            Result.failure(e)
        }
    }
}