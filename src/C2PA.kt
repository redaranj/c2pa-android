package info.guardianproject.c2pa

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

// MARK: - Error model matching iOS implementation

/**
 * C2PA error types matching iOS C2PAError
 */
sealed class C2PAError : Exception() {
    /**
     * API error from C2PA library
     */
    data class Api(override val message: String) : C2PAError()
    
    /**
     * Unexpected NULL pointer
     */
    object NilPointer : C2PAError() {
        override val message = "Unexpected NULL pointer"
    }
    
    /**
     * Invalid UTF-8 from C2PA
     */
    object Utf8 : C2PAError() {
        override val message = "Invalid UTF-8 from C2PA"
    }
    
    /**
     * Negative status from C API
     */
    data class Negative(val value: Long) : C2PAError() {
        override val message = "C2PA negative status $value"
    }
}

// MARK: - Helper functions matching iOS implementation

/**
 * Get string from C API with proper error handling
 */
private fun stringFromC(result: String?): String {
    if (result == null) {
        throw C2PAError.Api(C2PA.getError() ?: "Unknown C2PA error")
    }
    return result
}

/**
 * Get last C2PA error
 */
private fun lastC2PAError(): String = C2PA.getError() ?: "Unknown C2PA error"

/**
 * Guard against null pointer
 */
private fun <T> guardNotNull(value: T?): T {
    if (value == null) {
        throw C2PAError.Api(lastC2PAError())
    }
    return value
}

/**
 * Guard against negative status
 */
private fun guardNonNegative(value: Long): Long {
    if (value < 0) {
        throw C2PAError.Api(lastC2PAError())
    }
    return value
}

/**
 * C2PA version fetched once
 */
val C2PAVersion: String by lazy { C2PA.version() }

// MARK: - Signing layer matching iOS

/**
 * Signing algorithms matching iOS SigningAlgorithm
 */
enum class SigningAlgorithm(val description: String) {
    ES256("es256"),
    ES384("es384"),
    ES512("es512"),
    PS256("ps256"),
    PS384("ps384"),
    PS512("ps512"),
    ED25519("ed25519");
    
    override fun toString(): String = description
}

/**
 * Signer information matching iOS SignerInfo
 */
data class SignerInfo(
    val algorithm: SigningAlgorithm,
    val certificatePEM: String,
    val privateKeyPEM: String,
    val tsaURL: String? = null
) {
    // Internal fields for JNI compatibility
    internal val alg: String get() = algorithm.description
    internal val signCert: String get() = certificatePEM
    internal val privateKey: String get() = privateKeyPEM
    internal val taUrl: String? get() = tsaURL
}

// MARK: - Stream wrapper matching iOS

/**
 * Stream options matching iOS
 */
data class StreamOptions(val rawValue: UByte) {
    companion object {
        val read = StreamOptions((1u shl 0).toUByte())
        val write = StreamOptions((1u shl 1).toUByte())
    }
}

/**
 * C2PA Stream matching iOS Stream class
 */
// Type aliases moved outside class
typealias StreamReader = (buffer: ByteArray, count: Int) -> Int
typealias StreamSeeker = (offset: Long, origin: SeekMode) -> Long
typealias StreamWriter = (buffer: ByteArray, count: Int) -> Int
typealias StreamFlusher = () -> Int

abstract class Stream : Closeable {
    
    private var nativeHandle: Long = 0
    
    init {
        nativeHandle = createNativeStream()
    }
    
    abstract fun read(buffer: ByteArray, length: Long): Long
    abstract fun seek(offset: Long, mode: Int): Long
    abstract fun write(data: ByteArray, length: Long): Long
    abstract fun flush(): Long
    
    internal fun rawPtr(): Long = nativeHandle
    
    override fun close() {
        if (nativeHandle != 0L) {
            releaseNativeStream(nativeHandle)
            nativeHandle = 0
        }
    }
    
    private external fun createNativeStream(): Long
    private external fun releaseNativeStream(handle: Long)
    
    companion object {
        /**
         * Create stream from Data (matching iOS)
         */
        fun fromData(data: ByteArray): Stream {
            return MemoryStream(data)
        }
        
        /**
         * Create stream from file URL (matching iOS)
         */
        fun fromFileURL(
            url: java.net.URL,
            truncate: Boolean = true,
            createIfNeeded: Boolean = true
        ): Stream {
            val file = java.io.File(url.path)
            
            if (createIfNeeded && !file.exists()) {
                file.createNewFile()
            }
            
            val randomAccessFile = java.io.RandomAccessFile(file, "rw")
            if (truncate) {
                randomAccessFile.setLength(0)
            }
            
            return FileStream(randomAccessFile)
        }
    }
}

/**
 * Memory-based stream implementation
 */
private class MemoryStream(initialData: ByteArray = ByteArray(0)) : Stream() {
    private var data = initialData.copyOf()
    private var position = 0L
    
    fun getData(): ByteArray = data.copyOf()
    
    override fun read(buffer: ByteArray, length: Long): Long {
        val bytesToRead = minOf(length.toInt(), (data.size - position).toInt(), buffer.size)
        if (bytesToRead <= 0) return 0L
        
        System.arraycopy(data, position.toInt(), buffer, 0, bytesToRead)
        position += bytesToRead
        return bytesToRead.toLong()
    }
    
    override fun seek(offset: Long, mode: Int): Long {
        position = when (mode) {
            SeekMode.START.value -> offset
            SeekMode.CURRENT.value -> position + offset
            SeekMode.END.value -> data.size + offset
            else -> return -1L
        }
        
        if (position < 0) position = 0
        if (position > data.size) position = data.size.toLong()
        
        return position
    }
    
    override fun write(data: ByteArray, length: Long): Long {
        val bytesToWrite = minOf(length.toInt(), data.size)
        val newSize = maxOf(this.data.size, (position + bytesToWrite).toInt())
        
        if (newSize > this.data.size) {
            this.data = this.data.copyOf(newSize)
        }
        
        System.arraycopy(data, 0, this.data, position.toInt(), bytesToWrite)
        position += bytesToWrite
        
        return bytesToWrite.toLong()
    }
    
    override fun flush(): Long = 0L
}

/**
 * File-based stream implementation
 */
private class FileStream(private val file: java.io.RandomAccessFile) : Stream() {
    
    override fun read(buffer: ByteArray, length: Long): Long {
        return try {
            val bytesRead = file.read(buffer, 0, length.toInt())
            bytesRead.toLong()
        } catch (e: Exception) {
            -1L
        }
    }
    
    override fun seek(offset: Long, mode: Int): Long {
        return try {
            when (mode) {
                SeekMode.START.value -> file.seek(offset)
                SeekMode.CURRENT.value -> file.seek(file.filePointer + offset)
                SeekMode.END.value -> file.seek(file.length() + offset)
                else -> return -1L
            }
            file.filePointer
        } catch (e: Exception) {
            -1L
        }
    }
    
    override fun write(data: ByteArray, length: Long): Long {
        return try {
            file.write(data, 0, length.toInt())
            length
        } catch (e: Exception) {
            -1L
        }
    }
    
    override fun flush(): Long {
        return try {
            file.fd.sync()
            0L
        } catch (e: Exception) {
            -1L
        }
    }
    
    override fun close() {
        super.close()
        try {
            file.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}

// MARK: - Seek modes

enum class SeekMode(val value: Int) {
    START(0),
    CURRENT(1),
    END(2)
}

// MARK: - Reader matching iOS

/**
 * C2PA Reader matching iOS Reader class
 */
class Reader private constructor(private var nativeHandle: Long) : Closeable {
    
    companion object {
        /**
         * Create reader from stream
         */
        fun fromStream(format: String, stream: Stream): Reader {
            val handle = nativeFromStream(format, stream.rawPtr())
            return Reader(guardNotNull(if (handle != 0L) handle else null))
        }
        
        /**
         * Create reader from manifest data and stream
         */
        fun fromManifestDataAndStream(
            format: String,
            stream: Stream,
            manifest: ByteArray
        ): Reader {
            val handle = nativeFromManifestDataAndStream(format, stream.rawPtr(), manifest)
            return Reader(guardNotNull(if (handle != 0L) handle else null))
        }
        
        @JvmStatic
        private external fun nativeFromStream(format: String, streamHandle: Long): Long
        
        @JvmStatic
        private external fun nativeFromManifestDataAndStream(
            format: String,
            streamHandle: Long,
            manifestData: ByteArray
        ): Long
    }
    
    /**
     * Get JSON representation
     */
    fun json(): String = stringFromC(nativeJson(nativeHandle))
    
    /**
     * Write resource to stream
     */
    fun resource(uri: String, dest: Stream) {
        guardNonNegative(nativeResourceToStream(nativeHandle, uri, dest.rawPtr()))
    }
    
    override fun close() {
        if (nativeHandle != 0L) {
            free(nativeHandle)
            nativeHandle = 0
        }
    }
    
    private external fun free(handle: Long)
    private external fun nativeJson(handle: Long): String?
    private external fun nativeResourceToStream(handle: Long, uri: String, streamHandle: Long): Long
}

// MARK: - Builder matching iOS

/**
 * C2PA Builder matching iOS Builder class
 */
class Builder private constructor(private var nativeHandle: Long) : Closeable {
    
    companion object {
        /**
         * Create builder from JSON
         */
        fun fromJson(manifestJSON: String): Builder {
            val handle = nativeFromJson(manifestJSON)
            return Builder(guardNotNull(if (handle != 0L) handle else null))
        }
        
        /**
         * Create builder from archive stream
         */
        fun fromArchive(archiveStream: Stream): Builder {
            val handle = nativeFromArchive(archiveStream.rawPtr())
            return Builder(guardNotNull(if (handle != 0L) handle else null))
        }
        
        @JvmStatic
        private external fun nativeFromJson(manifestJson: String): Long
        
        @JvmStatic
        private external fun nativeFromArchive(streamHandle: Long): Long
    }
    
    /**
     * Set no-embed flag
     */
    fun setNoEmbed() = nativeSetNoEmbed(nativeHandle)
    
    /**
     * Set remote URL
     */
    fun setRemoteURL(url: String) {
        val result = nativeSetRemoteUrl(nativeHandle, url)
        if (result != 0) {
            throw C2PAError.Api(lastC2PAError())
        }
    }
    
    /**
     * Add resource
     */
    fun addResource(uri: String, stream: Stream) {
        val result = nativeAddResource(nativeHandle, uri, stream.rawPtr())
        if (result != 0) {
            throw C2PAError.Api(lastC2PAError())
        }
    }
    
    /**
     * Add ingredient
     */
    fun addIngredient(json: String, format: String, stream: Stream) {
        val result = nativeAddIngredientFromStream(nativeHandle, json, format, stream.rawPtr())
        if (result != 0) {
            throw C2PAError.Api(lastC2PAError())
        }
    }
    
    /**
     * Write archive
     */
    fun writeArchive(dest: Stream) {
        val result = nativeToArchive(nativeHandle, dest.rawPtr())
        if (result != 0) {
            throw C2PAError.Api(lastC2PAError())
        }
    }
    
    /**
     * Sign and return manifest bytes
     */
    fun sign(
        format: String,
        source: Stream,
        destination: Stream,
        signer: Signer
    ): ByteArray {
        val result = nativeSign(nativeHandle, format, source.rawPtr(), destination.rawPtr(), signer.rawPtr())
        
        if (result.size < 0) {
            throw C2PAError.Negative(result.size)
        }
        
        return result.manifestBytes ?: ByteArray(0)
    }
    
    override fun close() {
        if (nativeHandle != 0L) {
            free(nativeHandle)
            nativeHandle = 0
        }
    }
    
    // Sign result for JNI
    data class SignResult(val size: Long, val manifestBytes: ByteArray?)
    
    private external fun free(handle: Long)
    private external fun nativeSetNoEmbed(handle: Long)
    private external fun nativeSetRemoteUrl(handle: Long, remoteUrl: String): Int
    private external fun nativeAddResource(handle: Long, uri: String, streamHandle: Long): Int
    private external fun nativeAddIngredientFromStream(handle: Long, json: String, format: String, streamHandle: Long): Int
    private external fun nativeToArchive(handle: Long, streamHandle: Long): Int
    private external fun nativeSign(handle: Long, format: String, sourceHandle: Long, destHandle: Long, signerHandle: Long): SignResult
}

// MARK: - Signer matching iOS

/**
 * Callback interface for custom signing
 */
interface SignCallback {
    fun sign(data: ByteArray): ByteArray?
}

/**
 * C2PA Signer matching iOS Signer class
 */
class Signer private constructor(private var nativeHandle: Long) : Closeable {
    
    companion object {
        /**
         * Create signer from certificates and key PEM (matching iOS convenience init)
         */
        fun fromPEM(
            certsPEM: String,
            privateKeyPEM: String,
            algorithm: SigningAlgorithm,
            tsaURL: String? = null
        ): Signer {
            return fromInfo(SignerInfo(algorithm, certsPEM, privateKeyPEM, tsaURL))
        }
        
        /**
         * Create signer from SignerInfo
         */
        fun fromInfo(info: SignerInfo): Signer {
            val handle = nativeFromInfo(info)
            return Signer(guardNotNull(if (handle != 0L) handle else null))
        }
        
        /**
         * Create signer with callback (matching iOS)
         */
        fun fromCallback(
            algorithm: SigningAlgorithm,
            certificateChainPEM: String,
            tsaURL: String? = null,
            sign: (ByteArray) -> ByteArray
        ): Signer {
            val callback = object : SignCallback {
                override fun sign(data: ByteArray): ByteArray? {
                    return try {
                        sign(data)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            val handle = nativeFromCallback(algorithm.description, certificateChainPEM, tsaURL, callback)
            return Signer(guardNotNull(if (handle != 0L) handle else null))
        }
        
        /**
         * Create signer with Android Keystore
         */
        fun fromKeystore(
            algorithm: SigningAlgorithm,
            certificateChainPEM: String,
            tsaURL: String? = null,
            keystoreAlias: String
        ): Signer {
            return fromCallback(algorithm, certificateChainPEM, tsaURL) { data ->
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                
                val privateKey = keyStore.getKey(keystoreAlias, null) as? PrivateKey
                    ?: throw C2PAError.Api("Failed to find key '$keystoreAlias' in keystore")
                
                val signatureAlgorithm = when (algorithm) {
                    SigningAlgorithm.ES256 -> "SHA256withECDSA"
                    SigningAlgorithm.ES384 -> "SHA384withECDSA"
                    SigningAlgorithm.ES512 -> "SHA512withECDSA"
                    SigningAlgorithm.PS256 -> "SHA256withRSA/PSS"
                    SigningAlgorithm.PS384 -> "SHA384withRSA/PSS"
                    SigningAlgorithm.PS512 -> "SHA512withRSA/PSS"
                    SigningAlgorithm.ED25519 -> throw C2PAError.Api("Ed25519 not supported by Android Keystore")
                }
                
                val signature = Signature.getInstance(signatureAlgorithm)
                signature.initSign(privateKey)
                signature.update(data)
                signature.sign()
            }
        }
        
        /**
         * Create signer with web service
         */
        fun fromWebService(
            algorithm: SigningAlgorithm,
            certificateChainPEM: String,
            tsaURL: String? = null,
            requestBuilder: suspend (ByteArray) -> WebServiceRequest,
            responseParser: (ByteArray, Int) -> ByteArray = { data, _ -> data }
        ): Signer {
            return fromCallback(algorithm, certificateChainPEM, tsaURL) { data ->
                runBlocking {
                    val request = requestBuilder(data)
                    val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                        requestMethod = request.method
                        doOutput = request.body != null
                        request.headers.forEach { (key, value) ->
                            setRequestProperty(key, value)
                        }
                    }
                    
                    request.body?.let { body ->
                        connection.outputStream.use { it.write(body) }
                    }
                    
                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        throw C2PAError.Api("HTTP $responseCode")
                    }
                    
                    val responseData = connection.inputStream.use { it.readBytes() }
                    responseParser(responseData, responseCode)
                }
            }
        }
        
        @JvmStatic
        private external fun nativeFromInfo(signerInfo: SignerInfo): Long
        
        @JvmStatic
        private external fun nativeFromCallback(
            algorithm: String,
            certificateChain: String,
            tsaURL: String?,
            callback: SignCallback
        ): Long
    }
    
    /**
     * Get reserve size
     */
    fun reserveSize(): Int {
        val size = nativeReserveSize(nativeHandle)
        if (size < 0) {
            throw C2PAError.Negative(size)
        }
        return size.toInt()
    }
    
    internal fun rawPtr(): Long = nativeHandle
    
    override fun close() {
        if (nativeHandle != 0L) {
            free(nativeHandle)
            nativeHandle = 0
        }
    }
    
    private external fun nativeReserveSize(handle: Long): Long
    private external fun free(handle: Long)
}

// MARK: - Web service helpers

/**
 * Web service request data class
 */
data class WebServiceRequest(
    val url: String,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null
)

/**
 * Web service helpers matching iOS WebServiceHelpers
 */
object WebServiceHelpers {
    /**
     * Create basic POST request builder
     */
    fun basicPOSTRequestBuilder(
        url: String,
        authToken: String? = null,
        contentType: String = "application/octet-stream"
    ): suspend (ByteArray) -> WebServiceRequest {
        return { data ->
            WebServiceRequest(
                url = url,
                method = "POST",
                headers = buildMap {
                    put("Content-Type", contentType)
                    authToken?.let { put("Authorization", it) }
                },
                body = data
            )
        }
    }
    
    /**
     * Create JSON request builder
     */
    fun jsonRequestBuilder(
        url: String,
        authToken: String? = null,
        additionalFields: Map<String, Any> = emptyMap()
    ): suspend (ByteArray) -> WebServiceRequest {
        return { data ->
            val json = buildMap {
                putAll(additionalFields)
                put("data", android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP))
            }
            
            val jsonData = org.json.JSONObject(json).toString().toByteArray()
            
            WebServiceRequest(
                url = url,
                method = "POST",
                headers = buildMap {
                    put("Content-Type", "application/json")
                    authToken?.let { put("Authorization", it) }
                },
                body = jsonData
            )
        }
    }
    
    /**
     * Create JSON response parser
     */
    fun jsonResponseParser(signatureField: String = "signature"): (ByteArray, Int) -> ByteArray {
        return { data, _ ->
            val json = org.json.JSONObject(String(data))
            val signatureBase64 = json.getString(signatureField)
            android.util.Base64.decode(signatureBase64, android.util.Base64.NO_WRAP)
        }
    }
}

// MARK: - Whole-file helpers matching iOS

/**
 * C2PA main class matching iOS C2PA enum
 */
object C2PA {
    
    init {
        System.loadLibrary("c2pa_c")
        System.loadLibrary("c2pa_jni")
    }
    
    // MARK: - Hardware Security Extensions (StrongBox/TEE equivalent to iOS Secure Enclave)
    
    /**
     * Create hardware-backed C2PA signer using StrongBox/TEE
     * Equivalent to iOS Secure Enclave functionality
     */
    fun createHardwareBackedSigner(
        keyAlias: String,
        certificateChain: String,
        algorithm: String = "es256",
        requireStrongBox: Boolean = true,
        requireBiometric: Boolean = true
    ): Signer {
        // Check if key exists, if not generate it
        if (!HardwareSecurity.keyExists(keyAlias)) {
            val config = HardwareSecurity.HardwareKeyConfig(
                keyAlias = keyAlias,
                authConfig = HardwareSecurity.AuthenticationConfig(
                    requireBiometric = requireBiometric,
                    requireUserPresence = true,
                    userAuthenticationTimeoutSeconds = 30
                ),
                requireStrongBox = requireStrongBox
            )
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                HardwareSecurity.generateHardwareBackedKeyPair(config)
            } else {
                throw C2PAError.Api("Hardware-backed keys require Android 6.0+")
            }
        }
        
        return HardwareSecurity.createHardwareBackedSigner(keyAlias, certificateChain, algorithm)
            ?: throw C2PAError.Api("Failed to create hardware-backed signer")
    }
    
    /**
     * Create hardware-backed signer with biometric authentication
     * Matches iOS Secure Enclave with Touch ID/Face ID
     */
    fun createBiometricSigner(
        activity: androidx.fragment.app.FragmentActivity,
        keyAlias: String,
        certificateChain: String,
        algorithm: String = "es256",
        onSuccess: (Signer) -> Unit,
        onError: (String) -> Unit
    ) {
        HardwareSecurity.createBiometricSigner(
            activity, keyAlias, certificateChain, algorithm, onSuccess, onError
        )
    }
    
    /**
     * Get device hardware security capabilities
     */
    fun getHardwareSecurityCapabilities(context: android.content.Context): HardwareSecurity.SecurityCapabilities {
        return HardwareSecurity.getDeviceSecurityCapabilities(context)
    }
    
    /**
     * Generate hardware-backed key pair with attestation
     */
    fun generateHardwareKey(
        keyAlias: String,
        requireStrongBox: Boolean = true,
        requireBiometric: Boolean = true
    ): HardwareSecurity.GeneratedKeyPair {
        val config = HardwareSecurity.HardwareKeyConfig(
            keyAlias = keyAlias,
            authConfig = HardwareSecurity.AuthenticationConfig(
                requireBiometric = requireBiometric,
                requireUserPresence = true
            ),
            requireStrongBox = requireStrongBox
        )
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            HardwareSecurity.generateHardwareBackedKeyPair(config)
        } else {
            throw C2PAError.Api("Hardware-backed keys require Android 6.0+")
        }
    }
    
    /**
     * Get key attestation for verification (equivalent to iOS attestation)
     */
    fun getKeyAttestation(keyAlias: String): HardwareSecurity.AttestationResult {
        return HardwareSecurity.getKeyAttestation(keyAlias)
    }
    
    /**
     * Delete hardware-backed key
     */
    fun deleteHardwareKey(keyAlias: String): Boolean {
        return HardwareSecurity.deleteHardwareKey(keyAlias)
    }
    
    /**
     * Export public key in PEM format
     */
    fun exportHardwarePublicKey(keyAlias: String): String? {
        return HardwareSecurity.exportPublicKeyPEM(keyAlias)
    }
    
    /**
     * Read file (matching iOS)
     */
    fun readFile(url: java.net.URL, dataDir: java.net.URL? = null): String {
        return stringFromC(nativeReadFile(url.path, dataDir?.path))
    }
    
    /**
     * Read ingredient file (matching iOS)
     */
    fun readIngredientFile(url: java.net.URL, dataDir: java.net.URL? = null): String {
        val result = nativeReadIngredientFile(url.path, dataDir?.path)
        if (result == null) {
            val error = getError()
            if (error?.contains("null parameter data_dir") == true || error?.contains("data_dir") == true) {
                throw C2PAError.Api("No ingredient data found")
            }
            throw C2PAError.Api(error ?: "Unknown error reading ingredient")
        }
        return result
    }
    
    /**
     * Sign file (matching iOS)
     */
    fun signFile(
        source: java.net.URL,
        destination: java.net.URL,
        manifestJSON: String,
        signerInfo: SignerInfo,
        dataDir: java.net.URL? = null
    ) {
        val result = nativeSignFile(source.path, destination.path, manifestJSON, signerInfo, dataDir?.path)
        if (result == null) {
            throw C2PAError.Api(getError() ?: "Unknown error signing file")
        }
    }
    
    // Native method declarations
    external fun version(): String
    external fun getError(): String?
    external fun loadSettings(settings: String, format: String): Int
    private external fun nativeReadFile(path: String, dataDir: String?): String?
    private external fun nativeReadIngredientFile(path: String, dataDir: String?): String?
    private external fun nativeSignFile(sourcePath: String, destPath: String, manifest: String, signerInfo: SignerInfo, dataDir: String?): String?
    external fun ed25519Sign(data: ByteArray, privateKey: String): ByteArray?
}

// MARK: - Keystore helpers

/**
 * Keystore helpers for Android
 */
object KeystoreHelpers {
    /**
     * Generate key pair in Android Keystore
     */
    fun generateKeyPair(alias: String, algorithm: SigningAlgorithm): String {
        try {
            val keyPairGenerator = when (algorithm) {
                SigningAlgorithm.ES256, SigningAlgorithm.ES384, SigningAlgorithm.ES512 -> {
                    val spec = when (algorithm) {
                        SigningAlgorithm.ES256 -> "secp256r1"
                        SigningAlgorithm.ES384 -> "secp384r1"
                        SigningAlgorithm.ES512 -> "secp521r1"
                        else -> throw C2PAError.Api("Unsupported EC algorithm")
                    }
                    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                        initialize(
                            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                                .setAlgorithmParameterSpec(ECGenParameterSpec(spec))
                                .setDigests(
                                    when (algorithm) {
                                        SigningAlgorithm.ES256 -> KeyProperties.DIGEST_SHA256
                                        SigningAlgorithm.ES384 -> KeyProperties.DIGEST_SHA384
                                        SigningAlgorithm.ES512 -> KeyProperties.DIGEST_SHA512
                                        else -> throw C2PAError.Api("Unsupported digest")
                                    }
                                )
                                .build()
                        )
                    }
                }
                SigningAlgorithm.PS256, SigningAlgorithm.PS384, SigningAlgorithm.PS512 -> {
                    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                        initialize(
                            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                                .setKeySize(2048)
                                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                                .setDigests(
                                    when (algorithm) {
                                        SigningAlgorithm.PS256 -> KeyProperties.DIGEST_SHA256
                                        SigningAlgorithm.PS384 -> KeyProperties.DIGEST_SHA384
                                        SigningAlgorithm.PS512 -> KeyProperties.DIGEST_SHA512
                                        else -> throw C2PAError.Api("Unsupported digest")
                                    }
                                )
                                .build()
                        )
                    }
                }
                SigningAlgorithm.ED25519 -> throw C2PAError.Api("Ed25519 not supported by Android Keystore")
            }
            
            keyPairGenerator.generateKeyPair()
            return alias
        } catch (e: Exception) {
            throw C2PAError.Api("Failed to generate key pair: ${e.message}")
        }
    }
    
    /**
     * Export public key as PEM
     */
    fun exportPublicKeyPEM(alias: String): String {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            val certificate = keyStore.getCertificate(alias)
                ?: throw C2PAError.Api("Failed to find certificate for '$alias' in keystore")
            
            val publicKey = certificate.publicKey
            val encoded = publicKey.encoded
            val base64 = android.util.Base64.encodeToString(encoded, android.util.Base64.NO_WRAP)
            
            return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
        } catch (e: Exception) {
            when (e) {
                is C2PAError -> throw e
                else -> throw C2PAError.Api("Failed to export public key: ${e.message}")
            }
        }
    }
    
    /**
     * Delete key from keystore
     */
    fun deleteKey(alias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(alias)
            true
        } catch (e: Exception) {
            false
        }
    }
}