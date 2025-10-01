package org.contentauth.c2pa.exampleapp.data

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.WrappedKeyEntry
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.security.auth.x500.X500Principal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.ByteArrayStream
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.CertificateManager
import org.contentauth.c2pa.DataStream
import org.contentauth.c2pa.SignCallback
import org.contentauth.c2pa.Signer
import org.contentauth.c2pa.SigningAlgorithm
import org.contentauth.c2pa.exampleapp.model.SigningMode
import org.json.JSONArray
import org.json.JSONObject

class C2PAManager(
        private val context: Context,
        private val preferencesManager: PreferencesManager,
) {
    companion object {
        private const val TAG = "C2PAManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS_PREFIX = "C2PA_KEY_"

        // Using null for TSA URL to skip timestamping for testing
        private const val DEFAULT_TSA_URL = "" // Empty string to skip TSA
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

    suspend fun signImage(bitmap: Bitmap, location: Location? = null): Result<ByteArray> =
            withContext(Dispatchers.IO) {
                try {
                    // Convert bitmap to JPEG bytes
                    val imageBytes =
                            ByteArrayOutputStream().use { outputStream ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                                outputStream.toByteArray()
                            }

                    Log.d(TAG, "Original image size: ${imageBytes.size} bytes")

                    // Get current signing mode
                    val signingMode = preferencesManager.signingMode.first()
                    Log.d(TAG, "Using signing mode: $signingMode")

                    // Create manifest JSON
                    val manifestJSON = createManifestJSON(location, signingMode)

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
        Log.d(TAG, "Certificate length: ${defaultCertificate!!.length} chars")
        Log.d(TAG, "Private key length: ${defaultPrivateKey!!.length} chars")
        Log.d(
                TAG,
                "TSA URL: ${if (DEFAULT_TSA_URL.isEmpty()) "NONE (skipping timestamping)" else DEFAULT_TSA_URL}",
        )

        return try {
            Signer.fromKeys(
                    certsPEM = defaultCertificate!!,
                    privateKeyPEM = defaultPrivateKey!!,
                    algorithm = SigningAlgorithm.ES256,
                    tsaURL = if (DEFAULT_TSA_URL.isEmpty()) null else DEFAULT_TSA_URL,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create default signer", e)
            throw e
        }
    }

    private suspend fun createKeystoreSigner(): Signer {
        // Use Android Keystore for secure key storage
        requireNotNull(defaultCertificate) { "Default certificate not available" }
        requireNotNull(defaultPrivateKey) { "Default private key not available" }

        val keyAlias = "C2PA_SOFTWARE_KEY_SECURE"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Store certificate in preferences (certificates don't need hardware protection)
        var storedCert = preferencesManager.softwareCertificate.first()
        if (storedCert == null) {
            preferencesManager.setSoftwareCertificate(defaultCertificate!!)
            storedCert = defaultCertificate
        }

        // Android Keystore key import is complex and often fails with WrappedKeyEntry
        // For demo purposes, we'll show two approaches:
        // 1. Try Secure Key Import (often fails on real devices)
        // 2. If that fails, use the key directly but demonstrate Keystore with option 3 (hardware)

        if (!keyStore.containsAlias(keyAlias)) {
            try {
                Log.d(TAG, "Attempting to import private key into Android Keystore")
                importKeySecurely(keyAlias, defaultPrivateKey!!)
                Log.d(TAG, "Successfully imported key - using Android Keystore")

                // Use callback-based signing with the imported key
                val signCallback = createKeystoreSignCallback(keyAlias)
                return Signer.withCallback(
                        algorithm = SigningAlgorithm.ES256,
                        certificateChainPEM = storedCert!!,
                        tsaURL = if (DEFAULT_TSA_URL.isEmpty()) null else DEFAULT_TSA_URL,
                        sign = signCallback::sign,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Secure Key Import failed (common on many devices): ${e.message}")
                Log.w(TAG, "Falling back to direct key usage for option 2")
                Log.w(
                        TAG,
                        "Note: Use option 3 (Hardware) to see true Android Keystore with generated keys",
                )

                // Store key in preferences as encrypted fallback
                preferencesManager.setSoftwarePrivateKey(defaultPrivateKey!!)

                // Use the key directly - this still works for C2PA signing
                return Signer.fromKeys(
                        certsPEM = storedCert!!,
                        privateKeyPEM = defaultPrivateKey!!,
                        algorithm = SigningAlgorithm.ES256,
                        tsaURL = if (DEFAULT_TSA_URL.isEmpty()) null else DEFAULT_TSA_URL,
                )
            }
        } else {
            Log.d(TAG, "Using existing key from Android Keystore")
            val signCallback = createKeystoreSignCallback(keyAlias)
            return Signer.withCallback(
                    algorithm = SigningAlgorithm.ES256,
                    certificateChainPEM = storedCert!!,
                    tsaURL = if (DEFAULT_TSA_URL.isEmpty()) null else DEFAULT_TSA_URL,
                    sign = signCallback::sign,
            )
        }
    }

    private suspend fun createHardwareSigner(): Signer {
        val alias =
                preferencesManager.hardwareKeyAlias.first()
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
                tsaURL = if (DEFAULT_TSA_URL.isEmpty()) null else DEFAULT_TSA_URL,
                sign = signCallback::sign,
        )
    }

    private suspend fun createCustomSigner(): Signer {
        val certPEM =
                preferencesManager.customCertificate.first()
                        ?: throw IllegalStateException("Custom certificate not configured")
        val keyPEM =
                preferencesManager.customPrivateKey.first()
                        ?: throw IllegalStateException("Custom private key not configured")

        val keyAlias = "C2PA_CUSTOM_KEY_SECURE"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Check if we need to reimport (e.g., user uploaded new key)
        val lastKeyHash = preferencesManager.customKeyHash.first()
        val currentKeyHash = keyPEM.hashCode().toString()

        if (!keyStore.containsAlias(keyAlias) || lastKeyHash != currentKeyHash) {
            Log.d(TAG, "Importing custom private key into Android Keystore")

            // Remove old key if exists
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }

            // No fallbacks - if import fails, we fail
            importKeySecurely(keyAlias, keyPEM)
            Log.d(TAG, "Successfully imported custom key using Secure Key Import")
            preferencesManager.setCustomKeyHash(currentKeyHash)
        }

        Log.d(TAG, "Creating custom signer with imported key")

        // Use callback-based signing with the imported key
        val signCallback = createKeystoreSignCallback(keyAlias)
        return Signer.withCallback(
                algorithm = SigningAlgorithm.ES256,
                certificateChainPEM = certPEM,
                tsaURL = if (DEFAULT_TSA_URL.isEmpty()) null else DEFAULT_TSA_URL,
                sign = signCallback::sign,
        )
    }

    private suspend fun createRemoteSigner(): Signer {
        val remoteUrl =
                preferencesManager.remoteUrl.first()
                        ?: throw IllegalStateException("Remote signing URL not configured")
        val bearerToken = preferencesManager.remoteToken.first()

        val configUrl =
                if (remoteUrl.contains("/api/v1/c2pa/configuration")) {
                    remoteUrl
                } else {
                    "$remoteUrl/api/v1/c2pa/configuration"
                }

        Log.d(TAG, "Fetching configuration from: $configUrl")

        // Fetch configuration from remote service
        val request =
                Request.Builder()
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
        val certificateChainBase64 = configJson.getString("certificate_chain")

        // Decode the base64-encoded certificate chain
        val certificateChain =
                String(
                        android.util.Base64.decode(
                                certificateChainBase64,
                                android.util.Base64.DEFAULT
                        )
                )

        Log.d(TAG, "Creating remote signer with algorithm: $algorithm")

        // Create sign callback for remote signing
        val remoteSignCallback = createRemoteSignCallback(signingUrl, bearerToken)

        return Signer.withCallback(
                algorithm = SigningAlgorithm.valueOf(algorithm.uppercase()),
                certificateChainPEM = certificateChain,
                tsaURL = timestampUrl,
                sign = remoteSignCallback::sign,
        )
    }

    private fun createKeystoreKey(alias: String, useHardware: Boolean) {
        val keyPairGenerator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)

        val paramSpec =
                KeyGenParameterSpec.Builder(
                                alias,
                                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                        )
                        .apply {
                            setDigests(KeyProperties.DIGEST_SHA256)
                            setAlgorithmParameterSpec(
                                    java.security.spec.ECGenParameterSpec("secp256r1"),
                            )

                            if (useHardware) {
                                // Request hardware backing (StrongBox if available, TEE otherwise)
                                if (android.os.Build.VERSION.SDK_INT >=
                                                android.os.Build.VERSION_CODES.P
                                ) {
                                    setIsStrongBoxBacked(true)
                                }
                            }

                            // Self-signed certificate validity
                            setCertificateSubject(
                                    X500Principal("CN=C2PA Android User, O=C2PA Example, C=US"),
                            )
                            setCertificateSerialNumber(
                                    java.math.BigInteger.valueOf(System.currentTimeMillis()),
                            )
                            setCertificateNotBefore(Date())
                            setCertificateNotAfter(
                                    Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000),
                            )
                        }
                        .build()

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
        val remoteUrl =
                preferencesManager.remoteUrl.first()
                        ?: throw IllegalStateException("Remote URL required for hardware signing")
        val bearerToken = preferencesManager.remoteToken.first()

        // Generate CSR for the hardware key
        val csr = generateCSR(alias)

        // Submit CSR to signing server
        val enrollUrl = "$remoteUrl/api/v1/certificates/sign"

        val requestBody =
                JSONObject()
                        .apply {
                            put("csr", csr)
                            put(
                                    "metadata",
                                    JSONObject().apply {
                                        put("device_id", getDeviceId())
                                        put("app_version", getAppVersion())
                                    },
                            )
                        }
                        .toString()

        val request =
                Request.Builder()
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
        val certChain = responseJson.getString("certificate_chain")
        val certId = responseJson.getString("certificate_id")

        Log.d(TAG, "Certificate enrolled successfully. ID: $certId")

        return certChain
    }

    private fun generateCSR(alias: String): String {
        try {
            // Use the library's CertificateManager to generate a proper CSR
            val config =
                    CertificateManager.CertificateConfig(
                            commonName = "C2PA Hardware Key",
                            organization = "C2PA Example App",
                            organizationalUnit = "Mobile",
                            country = "US",
                            state = "CA",
                            locality = "San Francisco",
                    )

            // Generate CSR using the library
            val csr = CertificateManager.createCSR(alias, config)

            Log.d(TAG, "Generated proper CSR for alias $alias")
            return csr
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate CSR", e)
            throw RuntimeException("Failed to generate CSR: ${e.message}", e)
        }
    }

    private fun generateLocalCertificateChain(): Pair<String, String> {
        try {
            // Generate EC key pair for signing
            val keyPairGenerator = KeyPairGenerator.getInstance("EC")
            keyPairGenerator.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            val signerKeyPair = keyPairGenerator.generateKeyPair()

            // Generate Root CA key pair
            val rootKeyPair = keyPairGenerator.generateKeyPair()

            // Generate Intermediate CA key pair
            val intermediateKeyPair = keyPairGenerator.generateKeyPair()

            val validity = 365L * 10 // 10 years
            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + validity * 24 * 60 * 60 * 1000)

            // Create Root CA certificate (self-signed)
            val rootCert =
                    createCertificate(
                            subject = "CN=C2PA Android Root CA, O=C2PA Example App, C=US",
                            issuer = "CN=C2PA Android Root CA, O=C2PA Example App, C=US",
                            publicKey = rootKeyPair.public,
                            signerKey = rootKeyPair.private,
                            isCA = true,
                            notBefore = notBefore,
                            notAfter = notAfter,
                    )

            // Create Intermediate CA certificate (signed by Root)
            val intermediateCert =
                    createCertificate(
                            subject = "CN=C2PA Android Intermediate CA, O=C2PA Example App, C=US",
                            issuer = "CN=C2PA Android Root CA, O=C2PA Example App, C=US",
                            publicKey = intermediateKeyPair.public,
                            signerKey = rootKeyPair.private,
                            isCA = true,
                            notBefore = notBefore,
                            notAfter = notAfter,
                    )

            // Create End-Entity certificate (signed by Intermediate)
            val signerCert =
                    createCertificate(
                            subject = "CN=C2PA Android Signer, O=C2PA Example App, C=US",
                            issuer = "CN=C2PA Android Intermediate CA, O=C2PA Example App, C=US",
                            publicKey = signerKeyPair.public,
                            signerKey = intermediateKeyPair.private,
                            isCA = false,
                            notBefore = notBefore,
                            notAfter = notAfter,
                    )

            // Convert certificates to PEM format
            val certChainPEM = buildString {
                append("-----BEGIN CERTIFICATE-----\n")
                append(Base64.encodeToString(signerCert.encoded, Base64.NO_WRAP))
                append("\n-----END CERTIFICATE-----\n")
                append("-----BEGIN CERTIFICATE-----\n")
                append(Base64.encodeToString(intermediateCert.encoded, Base64.NO_WRAP))
                append("\n-----END CERTIFICATE-----\n")
            }

            // Convert private key to PEM format
            val privateKeyPEM = convertPrivateKeyToPEM(signerKeyPair.private)

            return Pair(certChainPEM, privateKeyPEM)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating local certificate chain", e)
            // Fallback to test certificates if generation fails
            return Pair(defaultCertificate!!, defaultPrivateKey!!)
        }
    }

    private fun createCertificate(
            subject: String,
            issuer: String,
            publicKey: java.security.PublicKey,
            signerKey: java.security.PrivateKey,
            isCA: Boolean,
            notBefore: Date,
            notAfter: Date,
    ): X509Certificate {
        // Use BouncyCastle or Android's certificate generation APIs
        // For now, using Android Keystore approach as a template
        // In production, you'd use BouncyCastle's X509v3CertificateBuilder

        // This is a simplified version - in reality you'd need proper certificate generation
        // Using test certificates as fallback for now
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val tempAlias = "TEMP_CERT_GEN_${System.currentTimeMillis()}"
        val keyPairGenerator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)

        val paramSpec =
                KeyGenParameterSpec.Builder(
                                tempAlias,
                                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                        )
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setAlgorithmParameterSpec(
                                java.security.spec.ECGenParameterSpec("secp256r1"),
                        )
                        .setCertificateSubject(X500Principal(subject))
                        .setCertificateSerialNumber(
                                java.math.BigInteger.valueOf(System.currentTimeMillis()),
                        )
                        .setCertificateNotBefore(notBefore)
                        .setCertificateNotAfter(notAfter)
                        .build()

        keyPairGenerator.initialize(paramSpec)
        keyPairGenerator.generateKeyPair()

        val cert = keyStore.getCertificate(tempAlias) as X509Certificate
        keyStore.deleteEntry(tempAlias) // Clean up temporary entry

        return cert
    }

    private fun convertPrivateKeyToPEM(privateKey: java.security.PrivateKey): String {
        return buildString {
            append("-----BEGIN EC PRIVATE KEY-----\n")
            append(Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP))
            append("\n-----END EC PRIVATE KEY-----\n")
        }
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

    /** Import key using Secure Key Import (API 28+) Throws exception if import fails */
    private fun importKeySecurely(keyAlias: String, privateKeyPEM: String) {
        try {
            Log.d(TAG, "Starting key import for alias: $keyAlias")
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // Parse the private key from PEM
            val privateKeyBytes = parsePrivateKeyFromPEM(privateKeyPEM)
            val keyFactory = KeyFactory.getInstance("EC")
            val privateKey =
                    keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes)) as
                            java.security.interfaces.ECPrivateKey

            Log.d(TAG, "Private key parsed, algorithm: ${privateKey.algorithm}")

            // Create wrapping key for import (using ENCRYPT/DECRYPT which is more widely supported)
            val wrappingKeyAlias = "${keyAlias}_WRAPPER_TEMP"

            // Clean up any existing wrapper key
            if (keyStore.containsAlias(wrappingKeyAlias)) {
                keyStore.deleteEntry(wrappingKeyAlias)
            }

            // Generate RSA wrapping key with ENCRYPT purpose (more compatible than WRAP_KEY)
            val keyGenSpec =
                    KeyGenParameterSpec.Builder(
                                    wrappingKeyAlias,
                                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setKeySize(2048)
                            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                            .build()

            val keyPairGenerator =
                    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
            keyPairGenerator.initialize(keyGenSpec)
            val wrappingKeyPair = keyPairGenerator.generateKeyPair()
            Log.d(TAG, "Wrapping key generated")

            // Get the public key for wrapping
            val publicKey = wrappingKeyPair.public

            // Wrap the private key
            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            cipher.init(Cipher.WRAP_MODE, publicKey)
            val wrappedKeyBytes = cipher.wrap(privateKey)
            Log.d(TAG, "Key wrapped, bytes length: ${wrappedKeyBytes.size}")

            // Import using WrappedKeyEntry
            val importSpec =
                    KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
                            .setAlgorithmParameterSpec(
                                    java.security.spec.ECGenParameterSpec("secp256r1"),
                            )
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .build()

            val wrappedKeyEntry =
                    WrappedKeyEntry(
                            wrappedKeyBytes,
                            wrappingKeyAlias,
                            "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
                            importSpec,
                    )

            keyStore.setEntry(keyAlias, wrappedKeyEntry, null)
            Log.d(TAG, "Key imported to keystore")

            // Clean up wrapping key
            keyStore.deleteEntry(wrappingKeyAlias)

            // Verify import
            if (keyStore.containsAlias(keyAlias)) {
                Log.d(TAG, "✅ Key successfully imported and verified in keystore")
            } else {
                throw IllegalStateException("Key not found after import")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Key import failed", e)
            Log.e(TAG, "Exception: ${e.javaClass.name}: ${e.message}")
            // Don't generate a wrong key - just fail and let the caller handle it
            throw IllegalStateException(
                    "Failed to import key using Secure Key Import: ${e.message}",
                    e,
            )
        }
    }

    /** Parse private key from PEM format */
    private fun parsePrivateKeyFromPEM(pem: String): ByteArray {
        val pemContent =
                pem.replace("-----BEGIN EC PRIVATE KEY-----", "")
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END EC PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("\\s".toRegex(), "")

        return Base64.decode(pemContent, Base64.NO_WRAP)
    }

    private fun createRemoteSignCallback(signingUrl: String, bearerToken: String?): SignCallback {
        return object : SignCallback {
            override fun sign(data: ByteArray): ByteArray {
                val requestBody =
                        JSONObject()
                                .apply {
                                    put("dataToSign", Base64.encodeToString(data, Base64.NO_WRAP))
                                }
                                .toString()

                val request =
                        Request.Builder()
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
            signer: Signer,
    ): ByteArray {
        Log.d(TAG, "Starting signImageData")
        Log.d(TAG, "Input image size: ${imageData.size} bytes")
        Log.d(TAG, "Manifest JSON: ${manifestJSON.take(200)}...") // First 200 chars

        // Create Builder with manifest
        Log.d(TAG, "Creating Builder from JSON")
        val builder = Builder.fromJson(manifestJSON)

        // Use ByteArrayStream which is designed for this purpose
        Log.d(TAG, "Creating streams")
        val sourceStream = DataStream(imageData)
        val destStream = ByteArrayStream()

        try {
            // Sign the image
            Log.d(TAG, "Calling builder.sign()")
            builder.sign(
                    format = "image/jpeg",
                    source = sourceStream,
                    dest = destStream,
                    signer = signer,
            )

            Log.d(TAG, "builder.sign() completed successfully")
            val result = destStream.getData()
            Log.d(TAG, "Output size: ${result.size} bytes")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error in signImageData", e)
            Log.e(TAG, "Error message: ${e.message}")
            Log.e(TAG, "Error cause: ${e.cause}")
            throw e
        } finally {
            // Make sure to close streams
            Log.d(TAG, "Closing streams")
            sourceStream.close()
            destStream.close()
        }
    }

    private fun createManifestJSON(location: Location?, signingMode: SigningMode): String {
        val manifest =
                JSONObject().apply {
                    put("claim_generator", "C2PA Android Example/1.0.0")
                    put("title", "Image signed on Android")

                    val assertions = JSONArray()

                    // Add location assertion if available
                    location?.let {
                        val locationAssertion =
                                JSONObject().apply {
                                    put("label", "stds.exif")
                                    put(
                                            "data",
                                            JSONObject().apply {
                                                put("exif:GPSLatitude", it.latitude)
                                                put("exif:GPSLongitude", it.longitude)
                                                put("exif:GPSAltitude", it.altitude)
                                                put(
                                                        "exif:GPSTimeStamp",
                                                        SimpleDateFormat(
                                                                        "yyyy-MM-dd'T'HH:mm:ss'Z'",
                                                                        Locale.US,
                                                                )
                                                                .apply {
                                                                    timeZone =
                                                                            TimeZone.getTimeZone(
                                                                                    "UTC",
                                                                            )
                                                                }
                                                                .format(Date(it.time)),
                                                )
                                            },
                                    )
                                }
                        assertions.put(locationAssertion)
                    }

                    // Add creation time assertion
                    val creationAssertion =
                            JSONObject().apply {
                                put("label", "c2pa.actions")
                                put(
                                        "data",
                                        JSONObject().apply {
                                            put(
                                                    "actions",
                                                    JSONArray().apply {
                                                        put(
                                                                JSONObject().apply {
                                                                    put("action", "c2pa.created")
                                                                    put(
                                                                            "when",
                                                                            SimpleDateFormat(
                                                                                            "yyyy-MM-dd'T'HH:mm:ss'Z'",
                                                                                            Locale.US,
                                                                                    )
                                                                                    .apply {
                                                                                        timeZone =
                                                                                                TimeZone.getTimeZone(
                                                                                                        "UTC",
                                                                                                )
                                                                                    }
                                                                                    .format(Date()),
                                                                    )
                                                                },
                                                        )
                                                    },
                                            )
                                        },
                                )
                            }
                    assertions.put(creationAssertion)

                    // Add signing method assertion
                    val signingMethodAssertion =
                            JSONObject().apply {
                                put("label", "c2pa.signing_method")
                                put(
                                        "data",
                                        JSONObject().apply {
                                            put("method", signingMode.name)
                                            put("description", signingMode.description)
                                        },
                                )
                            }
                    assertions.put(signingMethodAssertion)

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
                activeManifest.optString("title")?.let { Log.d(TAG, "Title: $it") }
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
                android.provider.Settings.Secure.ANDROID_ID,
        )
                ?: "unknown"
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
            val photosDir =
                    File(
                            context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
                            "C2PA",
                    )
            Log.d(TAG, "Gallery directory: ${photosDir.absolutePath}")
            Log.d(TAG, "Directory exists: ${photosDir.exists()}")

            if (!photosDir.exists()) {
                val created = photosDir.mkdirs()
                Log.d(TAG, "Directory created: $created")
            }

            val fileName = "C2PA_${System.currentTimeMillis()}.jpg"
            val file = File(photosDir, fileName)
            file.writeBytes(imageData)

            Log.d(TAG, "Image saved to: ${file.absolutePath}")
            Log.d(TAG, "File exists: ${file.exists()}")
            Log.d(TAG, "File size: ${file.length()} bytes")

            // Verify the file can be read back
            if (file.exists() && file.canRead()) {
                Log.d(TAG, "✅ File successfully saved and readable")
            } else {
                Log.e(TAG, "❌ File saved but cannot be read")
            }

            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image", e)
            Result.failure(e)
        }
    }
}
