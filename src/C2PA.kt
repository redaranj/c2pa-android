package org.contentauth.c2pa

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import java.util.Base64
import javax.crypto.Cipher

/**
 * Error model for C2PA operations
 */
sealed class C2PAError : Exception() {
    data class api(override val message: String) : C2PAError() {
        override fun toString() = "C2PA-API error: $message"
    }
    
    object nilPointer : C2PAError() {
        override fun toString() = "Unexpected NULL pointer"
    }
    
    object utf8 : C2PAError() {
        override fun toString() = "Invalid UTF-8 from C2PA"
    }
    
    data class negative(val value: Long) : C2PAError() {
        override fun toString() = "C2PA negative status $value"
    }
}

/**
 * C2PA version fetched once
 */
val C2PAVersion: String by lazy {
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
        System.loadLibrary("c2pa_c")
        System.loadLibrary("c2pa_jni")
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

    /**
     * Load settings from a string
     */
    @JvmStatic
    private external fun loadSettingsNative(settings: String, format: String): Int
    
    /**
     * Load settings from a string
     */
    @JvmStatic
    @Throws(C2PAError::class)
    fun loadSettings(settings: String, format: String) {
        try {
            val result = loadSettingsNative(settings, format)
            if (result < 0) {
                throw C2PAError.api(getError() ?: "Failed to load settings")
            }
        } catch (e: IllegalArgumentException) {
            throw C2PAError.api(e.message ?: "Invalid arguments")
        } catch (e: RuntimeException) {
            val error = getError()
            if (error != null) {
                throw C2PAError.api(error)
            }
            throw C2PAError.api(e.message ?: "Runtime error")
        }
    }

    /**
     * Read a manifest store from a file
     */
    @JvmStatic
    private external fun readFileNative(path: String, dataDir: String? = null): String?
    
    /**
     * Read a manifest store from a file
     */
    @JvmStatic
    @Throws(C2PAError::class)
    fun readFile(path: String, dataDir: String? = null): String {
        return try {
            readFileNative(path, dataDir) ?: throw C2PAError.api(getError() ?: "Failed to read file")
        } catch (e: IllegalArgumentException) {
            throw C2PAError.api(e.message ?: "Invalid arguments")
        } catch (e: RuntimeException) {
            val error = getError()
            if (error != null) {
                throw C2PAError.api(error)
            }
            throw C2PAError.api(e.message ?: "Runtime error")
        }
    }

    /**
     * Read an ingredient from a file
     */
    @JvmStatic
    private external fun readIngredientFileNative(path: String, dataDir: String? = null): String?
    
    /**
     * Read an ingredient from a file
     */
    @JvmStatic
    @Throws(C2PAError::class)
    fun readIngredientFile(path: String, dataDir: String? = null): String {
        return try {
            readIngredientFileNative(path, dataDir) ?: throw C2PAError.api(getError() ?: "Failed to read ingredient file")
        } catch (e: IllegalArgumentException) {
            throw C2PAError.api(e.message ?: "Invalid arguments")
        } catch (e: RuntimeException) {
            val error = getError()
            if (error != null) {
                throw C2PAError.api(error)
            }
            throw C2PAError.api(e.message ?: "Runtime error")
        }
    }

    /**
     * Sign a file with a manifest
     */
    @JvmStatic
    private external fun signFileNative(
        sourcePath: String,
        destPath: String,
        manifest: String,
        signerInfo: SignerInfo,
        dataDir: String? = null
    ): String?
    
    /**
     * Sign a file with a manifest
     */
    @JvmStatic
    @Throws(C2PAError::class)
    fun signFile(
        sourcePath: String,
        destPath: String,
        manifest: String,
        signerInfo: SignerInfo,
        dataDir: String? = null
    ): String {
        return try {
            signFileNative(sourcePath, destPath, manifest, signerInfo, dataDir)
                ?: throw C2PAError.api(getError() ?: "Failed to sign file")
        } catch (e: IllegalArgumentException) {
            throw C2PAError.api(e.message ?: "Invalid arguments")
        } catch (e: RuntimeException) {
            val error = getError()
            if (error != null) {
                throw C2PAError.api(error)
            }
            throw C2PAError.api(e.message ?: "Runtime error")
        }
    }

    /**
     * Sign data using Ed25519
     */
    @JvmStatic
    private external fun ed25519SignNative(data: ByteArray, privateKey: String): ByteArray?
    
    /**
     * Sign data using Ed25519
     */
    @JvmStatic
    @Throws(C2PAError::class)
    fun ed25519Sign(data: ByteArray, privateKey: String): ByteArray {
        return try {
            ed25519SignNative(data, privateKey) ?: throw C2PAError.api(getError() ?: "Failed to sign with Ed25519")
        } catch (e: IllegalArgumentException) {
            throw C2PAError.api(e.message ?: "Invalid arguments")
        } catch (e: RuntimeException) {
            val error = getError()
            if (error != null) {
                throw C2PAError.api(error)
            }
            throw C2PAError.api(e.message ?: "Runtime error")
        }
    }

    /**
     * Read a manifest from a file (convenience method)
     */
    @JvmStatic
    @Throws(C2PAError::class)
    fun read(from: File, resourcesDir: File? = null): String {
        return readFile(from.absolutePath, resourcesDir?.absolutePath)
    }

    /**
     * Sign a file (convenience method)
     */
    @JvmStatic
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
enum class C2paSeekMode(val value: Int) {
    Start(0),
    Current(1),
    End(2)
}

/**
 * Signing algorithms
 */
enum class SigningAlgorithm {
    es256, es384, es512, ps256, ps384, ps512, ed25519;
    
    val cValue: Int
        get() = ordinal
    
    val description: String
        get() = name
}

/**
 * Signer information
 */
data class SignerInfo(
    val algorithm: SigningAlgorithm,
    val certificatePEM: String,
    val privateKeyPEM: String,
    val tsaURL: String? = null
) {
    // For backward compatibility with old field names
    val alg: String get() = algorithm.description
    val signCert: String get() = certificatePEM
    val privateKey: String get() = privateKeyPEM
    val taUrl: String? get() = tsaURL
}

/**
 * Stream options
 */
class StreamOptions(val rawValue: Int) {
    companion object {
        val read = StreamOptions(1 shl 0)
        val write = StreamOptions(1 shl 1)
    }
}

// Type aliases for stream callbacks - moved outside of class
typealias StreamReader = (buffer: ByteArray, count: Int) -> Int
typealias StreamSeeker = (offset: Long, origin: C2paSeekMode) -> Long
typealias StreamWriter = (buffer: ByteArray, count: Int) -> Int
typealias StreamFlusher = () -> Int

/**
 * Abstract base class for C2PA streams
 */
abstract class Stream : Closeable {
    
    private var nativeHandle: Long = 0
    internal val rawPtr: Long get() = nativeHandle

    init {
        nativeHandle = createNativeStream()
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
            releaseNativeStream(nativeHandle)
            nativeHandle = 0
        }
    }

    private external fun createNativeStream(): Long
    private external fun releaseNativeStream(handle: Long)
}

/**
 * C2PA Reader for reading manifest stores
 */
class Reader private constructor(private var ptr: Long) : Closeable {
    
    companion object {
        // Libraries are automatically loaded by C2PA object initialization
        
        /**
         * Create a reader from a stream
         */
        @JvmStatic
        @Throws(C2PAError::class)
        operator fun invoke(format: String, stream: Stream): Reader {
            return try {
                val handle = fromStreamNative(format, stream.rawPtr)
                if (handle == 0L) {
                    throw C2PAError.api(C2PA.getError() ?: "Unknown error")
                }
                Reader(handle)
            } catch (e: IllegalArgumentException) {
                throw C2PAError.api(e.message ?: "Invalid arguments")
            } catch (e: RuntimeException) {
                // Check if this is a C2PA error by looking for an error message
                val error = C2PA.getError()
                if (error != null) {
                    throw C2PAError.api(error)
                }
                throw C2PAError.api(e.message ?: "Runtime error")
            }
        }

        /**
         * Create a reader from manifest data and stream
         */
        @JvmStatic
        @Throws(C2PAError::class)
        operator fun invoke(format: String, stream: Stream, manifest: ByteArray): Reader {
            return try {
                val handle = fromManifestDataAndStreamNative(format, stream.rawPtr, manifest)
                if (handle == 0L) {
                    throw C2PAError.api(C2PA.getError() ?: "Unknown error")
                }
                Reader(handle)
            } catch (e: IllegalArgumentException) {
                throw C2PAError.api(e.message ?: "Invalid arguments")
            } catch (e: RuntimeException) {
                val error = C2PA.getError()
                if (error != null) {
                    throw C2PAError.api(error)
                }
                throw C2PAError.api(e.message ?: "Runtime error")
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
            throw C2PAError.api(C2PA.getError() ?: "Failed to convert to JSON")
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
            throw C2PAError.api(C2PA.getError() ?: "Failed to write resource")
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
class Builder private constructor(private var ptr: Long) : Closeable {
    
    /**
     * Sign result containing size and optional manifest bytes
     */
    data class SignResult(val size: Long, val manifestBytes: ByteArray?)
    
    companion object {
        // Libraries are automatically loaded by C2PA object initialization
        
        /**
         * Create a builder from JSON
         */
        @JvmStatic
        @Throws(C2PAError::class)
        operator fun invoke(manifestJSON: String): Builder {
            return try {
                val handle = nativeFromJson(manifestJSON)
                if (handle == 0L) {
                    throw C2PAError.api(C2PA.getError() ?: "Failed to create builder from JSON")
                }
                Builder(handle)
            } catch (e: IllegalArgumentException) {
                throw C2PAError.api(e.message ?: "Invalid arguments")
            } catch (e: RuntimeException) {
                val error = C2PA.getError()
                if (error != null) {
                    throw C2PAError.api(error)
                }
                throw C2PAError.api(e.message ?: "Runtime error")
            }
        }

        /**
         * Create a builder from an archive stream
         */
        @JvmStatic
        @Throws(C2PAError::class)
        operator fun invoke(archive: Stream): Builder {
            return try {
                val handle = nativeFromArchive(archive.rawPtr)
                if (handle == 0L) {
                    throw C2PAError.api(C2PA.getError() ?: "Failed to create builder from archive")
                }
                Builder(handle)
            } catch (e: IllegalArgumentException) {
                throw C2PAError.api(e.message ?: "Invalid arguments")
            } catch (e: RuntimeException) {
                val error = C2PA.getError()
                if (error != null) {
                    throw C2PAError.api(error)
                }
                throw C2PAError.api(e.message ?: "Runtime error")
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
            throw C2PAError.api(C2PA.getError() ?: "Failed to set remote URL")
        }
    }

    /**
     * Add a resource to the builder
     */
    @Throws(C2PAError::class)
    fun addResource(uri: String, stream: Stream) {
        val result = addResourceNative(ptr, uri, stream.rawPtr)
        if (result < 0) {
            throw C2PAError.api(C2PA.getError() ?: "Failed to add resource")
        }
    }

    /**
     * Add an ingredient from a stream
     */
    @Throws(C2PAError::class)
    fun addIngredient(ingredientJSON: String, format: String, source: Stream) {
        val result = addIngredientFromStreamNative(ptr, ingredientJSON, format, source.rawPtr)
        if (result < 0) {
            throw C2PAError.api(C2PA.getError() ?: "Failed to add ingredient")
        }
    }

    /**
     * Write the builder to an archive
     */
    @Throws(C2PAError::class)
    fun toArchive(dest: Stream) {
        val result = toArchiveNative(ptr, dest.rawPtr)
        if (result < 0) {
            throw C2PAError.api(C2PA.getError() ?: "Failed to write archive")
        }
    }

    /**
     * Sign and write the manifest
     */
    @Throws(C2PAError::class)
    fun sign(format: String, source: Stream, dest: Stream, signer: Signer): SignResult {
        val result = signNative(ptr, format, source.rawPtr, dest.rawPtr, signer.ptr)
        if (result.size < 0) {
            throw C2PAError.api(C2PA.getError() ?: "Failed to sign")
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
            throw C2PAError.api(C2PA.getError() ?: "Failed to create placeholder")
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
            throw C2PAError.api(C2PA.getError() ?: "Failed to sign with data hash")
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
class Signer private constructor(internal var ptr: Long) : Closeable {
    
    companion object {
        // Libraries are automatically loaded by C2PA object initialization
        
        /**
         * Create signer from certificates and private key
         */
        @JvmStatic
        @Throws(C2PAError::class)
        operator fun invoke(
            certsPEM: String,
            privateKeyPEM: String,
            algorithm: SigningAlgorithm,
            tsaURL: String? = null
        ): Signer {
            val info = SignerInfo(algorithm, certsPEM, privateKeyPEM, tsaURL)
            return invoke(info)
        }

        /**
         * Create signer from SignerInfo
         */
        @JvmStatic
        @Throws(C2PAError::class)
        operator fun invoke(info: SignerInfo): Signer {
            return try {
                val handle = nativeFromInfo(info)
                if (handle == 0L) {
                    throw C2PAError.api(C2PA.getError() ?: "Failed to create signer")
                }
                Signer(handle)
            } catch (e: IllegalArgumentException) {
                throw C2PAError.api(e.message ?: "Invalid arguments")
            } catch (e: RuntimeException) {
                val error = C2PA.getError()
                if (error != null) {
                    throw C2PAError.api(error)
                }
                throw C2PAError.api(e.message ?: "Runtime error")
            }
        }

        /**
         * Create signer with custom signing callback
         */
        @JvmStatic
        @Throws(C2PAError::class)
        operator fun invoke(
            algorithm: SigningAlgorithm,
            certificateChainPEM: String,
            tsaURL: String? = null,
            sign: (ByteArray) -> ByteArray
        ): Signer {
            return try {
                val callback = object : SignCallback {
                    override fun sign(data: ByteArray): ByteArray = sign(data)
                }
                val handle = nativeFromCallback(algorithm.description, certificateChainPEM, tsaURL, callback)
                if (handle == 0L) {
                    throw C2PAError.api(C2PA.getError() ?: "Failed to create callback signer")
                }
                Signer(handle)
            } catch (e: IllegalArgumentException) {
                throw C2PAError.api(e.message ?: "Invalid arguments")
            } catch (e: RuntimeException) {
                val error = C2PA.getError()
                if (error != null) {
                    throw C2PAError.api(error)
                }
                throw C2PAError.api(e.message ?: "Runtime error")
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
     * Get the reserve size for this signer
     */
    @Throws(C2PAError::class)
    fun reserveSize(): Int {
        val size = reserveSizeNative(ptr)
        if (size < 0) {
            throw C2PAError.api(C2PA.getError() ?: "Failed to get reserve size")
        }
        if (size > Int.MAX_VALUE) {
            throw C2PAError.api("Reserve size too large: $size")
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
            C2paSeekMode.Start.value -> maxOf(0, minOf(data.size, safeOffset))
            C2paSeekMode.Current.value -> maxOf(0, minOf(data.size, cursor + safeOffset))
            C2paSeekMode.End.value -> maxOf(0, minOf(data.size, data.size + safeOffset))
            else -> return -1L
        }
        return cursor.toLong()
    }

    override fun write(data: ByteArray, length: Long): Long = -1L // Read-only
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
        return reader?.invoke(buffer, safeLen)?.toLong() ?: -1L
    }

    override fun seek(offset: Long, mode: Int): Long {
        val seekMode = C2paSeekMode.values().find { it.value == mode } ?: return -1L
        return seeker?.invoke(offset, seekMode) ?: -1L
    }

    override fun write(data: ByteArray, length: Long): Long {
        val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return writer?.invoke(data, safeLen)?.toLong() ?: -1L
    }

    override fun flush(): Long {
        return flusher?.invoke()?.toLong() ?: 0L
    }
}

/**
 * File-based stream
 */
fun Stream(fileURL: File, truncate: Boolean = true, createIfNeeded: Boolean = true): Stream {
    if (createIfNeeded && !fileURL.exists()) {
        fileURL.createNewFile()
    }
    
    val file = RandomAccessFile(fileURL, "rw")
    if (truncate) {
        file.setLength(0)
    }
    
    return object : Stream() {
        override fun read(buffer: ByteArray, length: Long): Long {
            return try {
                val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val bytesRead = file.read(buffer, 0, safeLen)
                bytesRead.toLong()
            } catch (e: Exception) {
                -1L
            }
        }

        override fun seek(offset: Long, mode: Int): Long {
            return try {
                when (mode) {
                    C2paSeekMode.Start.value -> file.seek(offset)
                    C2paSeekMode.Current.value -> file.seek(file.filePointer + offset)
                    C2paSeekMode.End.value -> file.seek(file.length() + offset)
                    else -> return -1L
                }
                file.filePointer
            } catch (e: Exception) {
                -1L
            }
        }

        override fun write(data: ByteArray, length: Long): Long {
            return try {
                val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                file.write(data, 0, safeLen)
                safeLen.toLong()
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
            try {
                file.close()
            } catch (e: Exception) {
                // Ignore
            }
            super.close()
        }
    }
}

/**
 * Web Service Signing Extension
 */
typealias WebServiceRequestBuilder = (ByteArray) -> HttpURLConnection
typealias WebServiceResponseParser = (ByteArray, Int) -> ByteArray

fun Signer(
    algorithm: SigningAlgorithm,
    certificateChainPEM: String,
    tsaURL: String? = null,
    requestBuilder: WebServiceRequestBuilder,
    responseParser: WebServiceResponseParser = { data, _ -> data }
): Signer {
    return Signer(algorithm, certificateChainPEM, tsaURL) { data ->
        val connection = requestBuilder(data)
        try {
            connection.connect()
            
            if (connection.responseCode !in 200..299) {
                throw C2PAError.api("HTTP ${connection.responseCode}")
            }
            
            val responseData = connection.inputStream.use { it.readBytes() }
            responseParser(responseData, connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Web Service Helpers
 */
enum class WebServiceHelpers {
    ; // Empty enum to match Swift pattern
    
    companion object {
        fun basicPOSTRequestBuilder(
            url: URL,
            authToken: String? = null,
            contentType: String = "application/octet-stream"
        ): WebServiceRequestBuilder = { data ->
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            authToken?.let {
                connection.setRequestProperty("Authorization", it)
            }
            connection.outputStream.use { it.write(data) }
            connection
        }
        
        fun jsonRequestBuilder(
            url: URL,
            authToken: String? = null,
            additionalFields: Map<String, Any> = emptyMap()
        ): WebServiceRequestBuilder = { data ->
            val json = mutableMapOf<String, Any>()
            json.putAll(additionalFields)
            json["data"] = Base64.getEncoder().encodeToString(data)
            
            val jsonBytes = json.toString().toByteArray()
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            authToken?.let {
                connection.setRequestProperty("Authorization", it)
            }
            connection.outputStream.use { it.write(jsonBytes) }
            connection
        }
        
        fun jsonResponseParser(signatureField: String = "signature"): WebServiceResponseParser = { data, _ ->
            val json = String(data)
            val regex = """"$signatureField"\s*:\s*"([^"]+)"""".toRegex()
            val match = regex.find(json) ?: throw C2PAError.api("Signature field not found in response")
            val signatureBase64 = match.groupValues[1]
            Base64.getDecoder().decode(signatureBase64)
        }
    }
}

/**
 * Keychain Signing Extension
 */
fun Signer(
    algorithm: SigningAlgorithm,
    certificateChainPEM: String,
    tsaURL: String? = null,
    keychainKeyTag: String
): Signer {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    
    val privateKey = keyStore.getKey(keychainKeyTag, null) as? PrivateKey
        ?: throw C2PAError.api("Key '$keychainKeyTag' not found in keystore")
    
    val javaAlgorithm = when (algorithm) {
        SigningAlgorithm.es256 -> "SHA256withECDSA"
        SigningAlgorithm.es384 -> "SHA384withECDSA"
        SigningAlgorithm.es512 -> "SHA512withECDSA"
        SigningAlgorithm.ps256 -> "SHA256withRSA/PSS"
        SigningAlgorithm.ps384 -> "SHA384withRSA/PSS"
        SigningAlgorithm.ps512 -> "SHA512withRSA/PSS"
        SigningAlgorithm.ed25519 -> throw C2PAError.api("Ed25519 not supported by Android Keystore")
    }
    
    return Signer(algorithm, certificateChainPEM, tsaURL) { data ->
        val signature = Signature.getInstance(javaAlgorithm)
        signature.initSign(privateKey)
        signature.update(data)
        signature.sign()
    }
}

/**
 * Helper Extensions
 */
fun exportPublicKeyPEM(fromKeychainTag: String): String {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    
    val certificate = keyStore.getCertificate(fromKeychainTag)
        ?: throw C2PAError.api("Certificate not found for alias: $fromKeychainTag")
    
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
    @JvmStatic
    @Throws(C2PAError::class)
    fun formatEmbeddable(format: String, manifestBytes: ByteArray): ByteArray {
        return try {
            val result = formatEmbeddableNative(format, manifestBytes)
            if (result == null) {
                throw C2PAError.api(C2PA.getError() ?: "Failed to format embeddable")
            }
            result
        } catch (e: IllegalArgumentException) {
            throw C2PAError.api(e.message ?: "Invalid arguments")
        } catch (e: RuntimeException) {
            val error = C2PA.getError()
            if (error != null) {
                throw C2PAError.api(error)
            }
            throw C2PAError.api(e.message ?: "Runtime error")
        }
    }

    @JvmStatic
    private external fun formatEmbeddableNative(format: String, manifestBytes: ByteArray): ByteArray?
}