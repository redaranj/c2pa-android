package org.contentauth.c2pa

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import java.util.Base64

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
 * Error model for C2PA operations
 */
sealed class C2PAError : Exception() {
    data class Api(override val message: String) : C2PAError() {
        override fun toString() = "C2PA-API error: $message"
    }
    
    object NilPointer : C2PAError() {
        override fun toString() = "Unexpected NULL pointer"
    }
    
    object Utf8 : C2PAError() {
        override fun toString() = "Invalid UTF-8 from C2PA"
    }
    
    data class Negative(val value: Long) : C2PAError() {
        override fun toString() = "C2PA negative status $value"
    }
}

/**
 * C2PA version fetched once
 */
val c2paVersion: String by lazy {
    C2PA.version()
}

/**
 * Main C2PA object for static operations
 * 
 * The native libraries are automatically loaded when this object is first accessed.
 * No manual initialization is required.
 */
object C2PA {
    init {
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
            // JVM (signing server): Load from file system
            val c2paServerLib = System.getProperty("c2pa.server.lib.path")
            val c2paServerJni = System.getProperty("c2pa.server.jni.path")
            
            if (c2paServerLib != null && c2paServerJni != null) {
                System.load(c2paServerLib)
                System.load(c2paServerJni)
            } else {
                // Fallback to relative paths
                val projectRoot = System.getProperty("user.dir")
                System.load("$projectRoot/signing-server/libs/libc2pa_c.dylib")
                System.load("$projectRoot/signing-server/libs/libc2pa_server_jni.dylib")
            }
        }
    }

    /**
     * Returns the version string of the C2PA library
     */
    @JvmStatic
    external fun version(): String

    /**
     * Returns the last error message, if any
     */
    @JvmStatic
    external fun getError(): String?

    @JvmStatic
    private external fun loadSettingsNative(settings: String, format: String): Int
    
    /**
     * Load settings from a string
     */
    @Throws(C2PAError::class)
    fun loadSettings(settings: String, format: String) {
        executeC2PAOperation("Failed to load settings") {
            val result = loadSettingsNative(settings, format)
            if (result < 0) null else Unit
        }
    }

    @JvmStatic
    private external fun readFileNative(path: String, dataDir: String? = null): String?
    
    /**
     * Read a manifest store from a file
     */
    @Throws(C2PAError::class)
    fun readFile(path: String, dataDir: String? = null): String {
        return executeC2PAOperation("Failed to read file") {
            readFileNative(path, dataDir)
        }
    }

    @JvmStatic
    private external fun readIngredientFileNative(path: String, dataDir: String? = null): String?
    
    /**
     * Read an ingredient from a file
     */
    @Throws(C2PAError::class)
    fun readIngredientFile(path: String, dataDir: String? = null): String {
        return executeC2PAOperation("Failed to read ingredient file") {
            readIngredientFileNative(path, dataDir)
        }
    }

    @JvmStatic
    private external fun signFileNative(
        sourcePath: String,
        destPath: String,
        manifest: String,
        algorithm: String,
        certificatePEM: String,
        privateKeyPEM: String,
        tsaURL: String?,
        dataDir: String? = null
    ): String?
    
    /**
     * Sign a file with a manifest
     */
    @Throws(C2PAError::class)
    fun signFile(
        sourcePath: String,
        destPath: String,
        manifest: String,
        signerInfo: SignerInfo,
        dataDir: String? = null
    ): String {
        return executeC2PAOperation("Failed to sign file") {
            signFileNative(
                sourcePath, 
                destPath, 
                manifest, 
                signerInfo.algorithm.description,
                signerInfo.certificatePEM,
                signerInfo.privateKeyPEM,
                signerInfo.tsaURL,
                dataDir
            )
        }
    }

    @JvmStatic
    private external fun ed25519SignNative(data: ByteArray, privateKey: String): ByteArray?
    
    /**
     * Sign data using Ed25519
     */
    @Throws(C2PAError::class)
    fun ed25519Sign(data: ByteArray, privateKey: String): ByteArray {
        return executeC2PAOperation("Failed to sign with Ed25519") {
            ed25519SignNative(data, privateKey)
        }
    }

    /**
     * Read a manifest from a file (convenience method)
     */
    @Throws(C2PAError::class)
    fun read(from: File, resourcesDir: File? = null): String {
        return readFile(from.absolutePath, resourcesDir?.absolutePath)
    }

    /**
     * Sign a file (convenience method)
     */
    @Throws(C2PAError::class)
    fun sign(
        source: File,
        destination: File,
        manifest: String,
        signer: SignerInfo,
        resourcesDir: File? = null
    ) {
        signFile(
            source.absolutePath,
            destination.absolutePath,
            manifest,
            signer,
            resourcesDir?.absolutePath
        )
    }
}

/**
 * Seek modes for stream operations
 */
enum class SeekMode(val value: Int) {
    START(0),
    CURRENT(1),
    END(2)
}

/**
 * Signing algorithms
 */
enum class SigningAlgorithm {
    ES256, ES384, ES512, PS256, PS384, PS512, ED25519;
    
    val cValue: Int
        get() = ordinal
    
    val description: String
        get() = name.lowercase()
}

/**
 * Signer information
 */
data class SignerInfo(
    val algorithm: SigningAlgorithm,
    val certificatePEM: String,
    val privateKeyPEM: String,
    val tsaURL: String? = null
)

// Type aliases for stream callbacks
typealias StreamReader = (buffer: ByteArray, count: Int) -> Int
typealias StreamSeeker = (offset: Long, origin: SeekMode) -> Long
typealias StreamWriter = (buffer: ByteArray, count: Int) -> Int
typealias StreamFlusher = () -> Int

/**
 * Abstract base class for C2PA streams
 */
abstract class Stream : Closeable {
    
    private var nativeHandle: Long = 0
    internal val rawPtr: Long get() = nativeHandle

    init {
        nativeHandle = createStreamNative()
    }

    /**
     * Read data from the stream
     */
    abstract fun read(buffer: ByteArray, length: Long): Long

    /**
     * Seek to a position in the stream
     */
    abstract fun seek(offset: Long, mode: Int): Long

    /**
     * Write data to the stream
     */
    abstract fun write(data: ByteArray, length: Long): Long

    /**
     * Flush the stream
     */
    abstract fun flush(): Long

    override fun close() {
        if (nativeHandle != 0L) {
            releaseStreamNative(nativeHandle)
            nativeHandle = 0
        }
    }

    private external fun createStreamNative(): Long
    private external fun releaseStreamNative(handle: Long)
}

/**
 * C2PA Reader for reading manifest stores
 */
class Reader internal constructor(private var ptr: Long) : Closeable {
    
    companion object {
        /**
         * Create a reader from a stream
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromStream(format: String, stream: Stream): Reader {
            return executeC2PAOperation("Failed to create reader from stream") {
                val handle = fromStreamNative(format, stream.rawPtr)
                if (handle == 0L) null else Reader(handle)
            }
        }

        /**
         * Create a reader from manifest data and stream
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromManifestAndStream(format: String, stream: Stream, manifest: ByteArray): Reader {
            return executeC2PAOperation("Failed to create reader from manifest and stream") {
                val handle = fromManifestDataAndStreamNative(format, stream.rawPtr, manifest)
                if (handle == 0L) null else Reader(handle)
            }
        }

        @JvmStatic
        private external fun fromStreamNative(format: String, streamHandle: Long): Long

        @JvmStatic
        private external fun fromManifestDataAndStreamNative(
            format: String,
            streamHandle: Long,
            manifestData: ByteArray
        ): Long
    }

    /**
     * Convert the reader to JSON
     */
    @Throws(C2PAError::class)
    fun json(): String {
        val json = toJsonNative(ptr)
        if (json == null) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to convert to JSON")
        }
        return json
    }

    /**
     * Write a resource to a stream
     */
    @Throws(C2PAError::class)
    fun resource(uri: String, to: Stream) {
        val result = resourceToStreamNative(ptr, uri, to.rawPtr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to write resource")
        }
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0
        }
    }

    private external fun free(handle: Long)
    private external fun toJsonNative(handle: Long): String?
    private external fun resourceToStreamNative(handle: Long, uri: String, streamHandle: Long): Long
}

/**
 * C2PA Builder for creating manifest stores
 */
class Builder internal constructor(private var ptr: Long) : Closeable {
    
    /**
     * Sign result containing size and optional manifest bytes
     */
    data class SignResult(val size: Long, val manifestBytes: ByteArray?)
    
    companion object {
        /**
         * Create a builder from JSON
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromJson(manifestJSON: String): Builder {
            return executeC2PAOperation("Failed to create builder from JSON") {
                val handle = nativeFromJson(manifestJSON)
                if (handle == 0L) null else Builder(handle)
            }
        }

        /**
         * Create a builder from an archive stream
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromArchive(archive: Stream): Builder {
            return executeC2PAOperation("Failed to create builder from archive") {
                val handle = nativeFromArchive(archive.rawPtr)
                if (handle == 0L) null else Builder(handle)
            }
        }

        @JvmStatic
        private external fun nativeFromJson(manifestJson: String): Long

        @JvmStatic
        private external fun nativeFromArchive(streamHandle: Long): Long
    }

    /**
     * Set the no-embed flag
     */
    fun setNoEmbed() = setNoEmbedNative(ptr)

    /**
     * Set the remote URL
     */
    @Throws(C2PAError::class)
    fun setRemoteURL(url: String) {
        val result = setRemoteUrlNative(ptr, url)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set remote URL")
        }
    }

    /**
     * Add a resource to the builder
     */
    @Throws(C2PAError::class)
    fun addResource(uri: String, stream: Stream) {
        val result = addResourceNative(ptr, uri, stream.rawPtr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to add resource")
        }
    }

    /**
     * Add an ingredient from a stream
     */
    @Throws(C2PAError::class)
    fun addIngredient(ingredientJSON: String, format: String, source: Stream) {
        val result = addIngredientFromStreamNative(ptr, ingredientJSON, format, source.rawPtr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to add ingredient")
        }
    }

    /**
     * Write the builder to an archive
     */
    @Throws(C2PAError::class)
    fun toArchive(dest: Stream) {
        val result = toArchiveNative(ptr, dest.rawPtr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to write archive")
        }
    }

    /**
     * Sign and write the manifest
     */
    @Throws(C2PAError::class)
    fun sign(format: String, source: Stream, dest: Stream, signer: Signer): SignResult {
        val result = signNative(ptr, format, source.rawPtr, dest.rawPtr, signer.ptr)
        if (result.size < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to sign")
        }
        return result
    }

    /**
     * Create a hashed placeholder for later signing
     */
    @Throws(C2PAError::class)
    fun dataHashedPlaceholder(reservedSize: Long, format: String): ByteArray {
        val result = dataHashedPlaceholderNative(ptr, reservedSize, format)
        if (result == null) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to create placeholder")
        }
        return result
    }

    /**
     * Sign using data hash (advanced use)
     */
    @Throws(C2PAError::class)
    fun signDataHashedEmbeddable(
        signer: Signer,
        dataHash: String,
        format: String,
        asset: Stream? = null
    ): ByteArray {
        val result = signDataHashedEmbeddableNative(
            ptr,
            signer.ptr,
            dataHash,
            format,
            asset?.rawPtr ?: 0L
        )
        if (result == null) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to sign with data hash")
        }
        return result
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0
        }
    }

    private external fun free(handle: Long)
    private external fun setNoEmbedNative(handle: Long)
    private external fun setRemoteUrlNative(handle: Long, remoteUrl: String): Int
    private external fun addResourceNative(handle: Long, uri: String, streamHandle: Long): Int
    private external fun addIngredientFromStreamNative(
        handle: Long,
        ingredientJson: String,
        format: String,
        sourceHandle: Long
    ): Int
    private external fun toArchiveNative(handle: Long, streamHandle: Long): Int
    private external fun signNative(
        handle: Long,
        format: String,
        sourceHandle: Long,
        destHandle: Long,
        signerHandle: Long
    ): SignResult
    private external fun dataHashedPlaceholderNative(
        handle: Long,
        reservedSize: Long,
        format: String
    ): ByteArray?
    private external fun signDataHashedEmbeddableNative(
        handle: Long,
        signerHandle: Long,
        dataHash: String,
        format: String,
        assetHandle: Long
    ): ByteArray?
}

/**
 * C2PA Signer for signing manifests
 */
class Signer internal constructor(internal var ptr: Long) : Closeable {
    
    companion object {
        /**
         * Create signer from certificates and private key
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromKeys(
            certsPEM: String,
            privateKeyPEM: String,
            algorithm: SigningAlgorithm,
            tsaURL: String? = null
        ): Signer {
            val info = SignerInfo(algorithm, certsPEM, privateKeyPEM, tsaURL)
            return fromInfo(info)
        }

        /**
         * Create signer from SignerInfo
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromInfo(info: SignerInfo): Signer {
            return executeC2PAOperation("Failed to create signer") {
                val handle = nativeFromInfo(
                    info.algorithm.description,
                    info.certificatePEM,
                    info.privateKeyPEM,
                    info.tsaURL
                )
                if (handle == 0L) null else Signer(handle)
            }
        }

        /**
         * Create signer with custom signing callback
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun withCallback(
            algorithm: SigningAlgorithm,
            certificateChainPEM: String,
            tsaURL: String? = null,
            sign: (ByteArray) -> ByteArray
        ): Signer {
            return executeC2PAOperation("Failed to create callback signer") {
                val callback = object : SignCallback {
                    override fun sign(data: ByteArray): ByteArray = sign(data)
                }
                val handle = nativeFromCallback(algorithm.description, certificateChainPEM, tsaURL, callback)
                if (handle == 0L) null else Signer(handle)
            }
        }

        @JvmStatic
        private external fun nativeFromInfo(
            algorithm: String,
            certificatePEM: String,
            privateKeyPEM: String,
            tsaURL: String?
        ): Long

        @JvmStatic
        private external fun nativeFromCallback(
            algorithm: String,
            certificateChain: String,
            tsaURL: String?,
            callback: SignCallback
        ): Long
        
        /**
         * Create signer for web service signing
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun withWebService(
            algorithm: SigningAlgorithm,
            certificateChainPEM: String,
            tsaURL: String? = null,
            signer: WebServiceSigner
        ): Signer {
            return withCallback(algorithm, certificateChainPEM, tsaURL, signer)
        }
        
        /**
         * Create signer for Android Keystore
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun withKeystore(
            algorithm: SigningAlgorithm,
            certificateChainPEM: String,
            tsaURL: String? = null,
            keystoreAlias: String
        ): Signer {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            val privateKey = keyStore.getKey(keystoreAlias, null) as? PrivateKey
                ?: throw C2PAError.Api("Key '$keystoreAlias' not found in keystore")
            
            val javaAlgorithm = when (algorithm) {
                SigningAlgorithm.ES256 -> "SHA256withECDSA"
                SigningAlgorithm.ES384 -> "SHA384withECDSA"
                SigningAlgorithm.ES512 -> "SHA512withECDSA"
                SigningAlgorithm.PS256 -> "SHA256withRSA/PSS"
                SigningAlgorithm.PS384 -> "SHA384withRSA/PSS"
                SigningAlgorithm.PS512 -> "SHA512withRSA/PSS"
                SigningAlgorithm.ED25519 -> throw C2PAError.Api("Ed25519 not supported by Android Keystore")
            }
            
            return withCallback(algorithm, certificateChainPEM, tsaURL) { data ->
                val signature = Signature.getInstance(javaAlgorithm)
                signature.initSign(privateKey)
                signature.update(data)
                signature.sign()
            }
        }
    }

    /**
     * Get the reserve size for this signer
     */
    @Throws(C2PAError::class)
    fun reserveSize(): Int {
        val size = reserveSizeNative(ptr)
        if (size < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to get reserve size")
        }
        if (size > Int.MAX_VALUE) {
            throw C2PAError.Api("Reserve size too large: $size")
        }
        return size.toInt()
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0L
        }
    }

    private external fun reserveSizeNative(handle: Long): Long
    private external fun free(handle: Long)
}

/**
 * Callback interface for custom signing operations
 */
interface SignCallback {
    fun sign(data: ByteArray): ByteArray
}

/**
 * Stream implementation backed by Data
 */
class DataStream(private val data: ByteArray) : Stream() {
    private var cursor = 0
    
    override fun read(buffer: ByteArray, length: Long): Long {
        val remain = data.size - cursor
        if (remain <= 0) return 0L
        val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val n = minOf(remain, safeLen)
        System.arraycopy(data, cursor, buffer, 0, n)
        cursor += n
        return n.toLong()
    }

    override fun seek(offset: Long, mode: Int): Long {
        val safeOffset = offset.coerceIn(-Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        cursor = when (mode) {
            SeekMode.START.value -> maxOf(0, minOf(data.size, safeOffset))
            SeekMode.CURRENT.value -> maxOf(0, minOf(data.size, cursor + safeOffset))
            SeekMode.END.value -> maxOf(0, minOf(data.size, data.size + safeOffset))
            else -> return -1L
        }
        return cursor.toLong()
    }

    override fun write(data: ByteArray, length: Long): Long = throw UnsupportedOperationException("DataStream is read-only")
    override fun flush(): Long = 0L
}

/**
 * Stream implementation with callbacks
 */
class CallbackStream(
    private val reader: StreamReader? = null,
    private val seeker: StreamSeeker? = null,
    private val writer: StreamWriter? = null,
    private val flusher: StreamFlusher? = null
) : Stream() {
    
    override fun read(buffer: ByteArray, length: Long): Long {
        val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return reader?.invoke(buffer, safeLen)?.toLong() 
            ?: throw UnsupportedOperationException("Read operation not supported: no reader callback provided")
    }

    override fun seek(offset: Long, mode: Int): Long {
        val seekMode = SeekMode.values().find { it.value == mode } 
            ?: throw IllegalArgumentException("Invalid seek mode: $mode")
        return seeker?.invoke(offset, seekMode) 
            ?: throw UnsupportedOperationException("Seek operation not supported: no seeker callback provided")
    }

    override fun write(data: ByteArray, length: Long): Long {
        val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return writer?.invoke(data, safeLen)?.toLong() 
            ?: throw UnsupportedOperationException("Write operation not supported: no writer callback provided")
    }

    override fun flush(): Long {
        return flusher?.invoke()?.toLong() 
            ?: throw UnsupportedOperationException("Flush operation not supported: no flusher callback provided")
    }
}

/**
 * File-based stream implementation
 */
class FileStream(
    fileURL: File,
    mode: Mode = Mode.READ_WRITE,
    createIfNeeded: Boolean = true
) : Stream() {
    
    enum class Mode {
        READ,
        WRITE,
        READ_WRITE
    }
    
    private val file: RandomAccessFile
    
    init {
        if (createIfNeeded && !fileURL.exists()) {
            fileURL.createNewFile()
        }
        
        val fileMode = when (mode) {
            Mode.READ -> "r"
            Mode.WRITE, Mode.READ_WRITE -> "rw"
        }
        
        file = RandomAccessFile(fileURL, fileMode)
        if (mode == Mode.WRITE) {
            file.setLength(0)
        }
    }
    
    override fun read(buffer: ByteArray, length: Long): Long {
        return try {
            val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val bytesRead = file.read(buffer, 0, safeLen)
            // Convert -1 (EOF) to 0L for consistency with DataStream
            if (bytesRead == -1) 0L else bytesRead.toLong()
        } catch (e: Exception) {
            throw IOException("Failed to read from file", e)
        }
    }

    override fun seek(offset: Long, mode: Int): Long {
        return try {
            // Validate mode before any file operations
            val newPosition = when (mode) {
                SeekMode.START.value -> offset
                SeekMode.CURRENT.value -> file.filePointer + offset
                SeekMode.END.value -> file.length() + offset
                else -> throw IllegalArgumentException("Invalid seek mode: $mode")
            }
            file.seek(newPosition)
            file.filePointer
        } catch (e: IllegalArgumentException) {
            throw e // Re-throw for invalid mode
        } catch (e: Exception) {
            throw IOException("Failed to seek in file", e)
        }
    }

    override fun write(data: ByteArray, length: Long): Long {
        return try {
            val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            file.write(data, 0, safeLen)
            safeLen.toLong()
        } catch (e: Exception) {
            throw IOException("Failed to write to file", e)
        }
    }

    override fun flush(): Long {
        return try {
            file.fd.sync()
            0L
        } catch (e: Exception) {
            throw IOException("Failed to flush file", e)
        }
    }
    
    override fun close() {
        try {
            file.close()
        } catch (e: Exception) {
            // Ignore
        }
        super.close()
    }
}

/**
 * Read-write stream with growable byte array.
 * Use this for in-memory operations that need to write output.
 */
class ByteArrayStream(initialData: ByteArray? = null) : Stream() {
    private val buffer = ByteArrayOutputStream()
    private var position = 0
    private var data: ByteArray = initialData ?: ByteArray(0)
    
    init {
        initialData?.let {
            buffer.write(it)
        }
    }
    
    override fun read(buffer: ByteArray, length: Long): Long {
        if (position >= data.size) return 0
        val toRead = minOf(length.toInt(), data.size - position)
        System.arraycopy(data, position, buffer, 0, toRead)
        position += toRead
        return toRead.toLong()
    }
    
    override fun seek(offset: Long, mode: Int): Long {
        position = when (mode) {
            SeekMode.START.value -> offset.toInt()
            SeekMode.CURRENT.value -> position + offset.toInt()
            SeekMode.END.value -> data.size + offset.toInt()
            else -> return -1L
        }
        position = position.coerceIn(0, data.size)
        return position.toLong()
    }
    
    override fun write(writeData: ByteArray, length: Long): Long {
        val len = length.toInt()
        if (position < data.size) {
            // Writing in the middle - need to handle carefully
            val newData = data.toMutableList()
            for (i in 0 until len) {
                if (position + i < newData.size) {
                    newData[position + i] = writeData[i]
                } else {
                    newData.add(writeData[i])
                }
            }
            data = newData.toByteArray()
            buffer.reset()
            buffer.write(data)
        } else {
            // Appending
            buffer.write(writeData, 0, len)
            data = buffer.toByteArray()
        }
        position += len
        return length
    }
    
    override fun flush(): Long {
        data = buffer.toByteArray()
        return 0
    }
    
    /**
     * Get the current data in the stream
     */
    fun getData(): ByteArray = data
}

/**
 * Type aliases for web service signing
 */
typealias WebServiceSigner = (ByteArray) -> ByteArray

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