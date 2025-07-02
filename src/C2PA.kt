package org.contentauth.c2pa

import java.io.Closeable

/**
 * Main C2PA class for static operations
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
     * @param settings The settings string
     * @param format The format of the settings
     * @return 0 on success, -1 on error
     */
    @JvmStatic
    external fun loadSettings(settings: String, format: String): Int

    /**
     * Read a manifest store from a file
     * @param path The file path
     * @param dataDir Optional directory for binary resources
     * @return JSON string of the manifest store, or null on error
     */
    @JvmStatic
    external fun readFile(path: String, dataDir: String? = null): String?

    /**
     * Read an ingredient from a file
     * @param path The file path
     * @param dataDir Optional directory for binary resources
     * @return JSON string of the ingredient, or null on error
     */
    @JvmStatic
    external fun readIngredientFile(path: String, dataDir: String? = null): String?

    /**
     * Sign a file with a manifest
     * @param sourcePath Source file path
     * @param destPath Destination file path
     * @param manifest Manifest JSON string
     * @param signerInfo Signer information
     * @param dataDir Optional data directory
     * @return Result string or null on error
     */
    @JvmStatic
    external fun signFile(
        sourcePath: String,
        destPath: String,
        manifest: String,
        signerInfo: SignerInfo,
        dataDir: String? = null
    ): String?

    /**
     * Sign data using Ed25519
     * @param data Data to sign
     * @param privateKey Private key in PEM format
     * @return Signature bytes or null on error
     */
    @JvmStatic
    external fun ed25519Sign(data: ByteArray, privateKey: String): ByteArray?
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
enum class SigningAlg(val value: Int) {
    ES256(0),
    ES384(1),
    ES512(2),
    PS256(3),
    PS384(4),
    PS512(5),
    ED25519(6)
}

/**
 * Signer information data class
 */
data class SignerInfo(
    val alg: String,
    val signCert: String,
    val privateKey: String,
    val taUrl: String? = null
)

/**
 * Abstract base class for C2PA streams
 */
abstract class C2PAStream : Closeable {
    private var nativeHandle: Long = 0

    init {
        nativeHandle = createNativeStream()
    }

    /**
     * Read data from the stream
     * @param buffer Buffer to read into
     * @param length Number of bytes to read
     * @return Number of bytes read, or negative on error
     */
    abstract fun read(buffer: ByteArray, length: Long): Long

    /**
     * Seek to a position in the stream
     * @param offset Offset to seek
     * @param mode Seek mode
     * @return New position, or negative on error
     */
    abstract fun seek(offset: Long, mode: Int): Long

    /**
     * Write data to the stream
     * @param data Data to write
     * @param length Number of bytes to write
     * @return Number of bytes written, or negative on error
     */
    abstract fun write(data: ByteArray, length: Long): Long

    /**
     * Flush the stream
     * @return 0 on success, negative on error
     */
    abstract fun flush(): Long

    /**
     * Get the native handle
     */
    internal fun getNativeHandle(): Long = nativeHandle

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
class C2PAReader private constructor(private var nativeHandle: Long) : Closeable {
    
    companion object {
        /**
         * Create a reader from a stream
         * @param format Mime type or extension
         * @param stream The stream to read from
         * @return Reader instance or null on error
         */
        @JvmStatic
        fun fromStream(format: String, stream: C2PAStream): C2PAReader? {
            val handle = fromStream(format, stream.getNativeHandle())
            return if (handle != 0L) C2PAReader(handle) else null
        }

        /**
         * Create a reader from manifest data and stream
         * @param format Mime type or extension
         * @param stream The stream to read from
         * @param manifestData Manifest data bytes
         * @return Reader instance or null on error
         */
        @JvmStatic
        fun fromManifestDataAndStream(
            format: String,
            stream: C2PAStream,
            manifestData: ByteArray
        ): C2PAReader? {
            val handle = fromManifestDataAndStream(format, stream.getNativeHandle(), manifestData)
            return if (handle != 0L) C2PAReader(handle) else null
        }

        @JvmStatic
        private external fun fromStream(format: String, streamHandle: Long): Long

        @JvmStatic
        private external fun fromManifestDataAndStream(
            format: String,
            streamHandle: Long,
            manifestData: ByteArray
        ): Long
    }

    /**
     * Convert the reader to JSON
     * @return JSON string representation
     */
    fun toJson(): String = toJson(nativeHandle)

    /**
     * Write a resource to a stream
     * @param uri Resource URI
     * @param stream Stream to write to
     * @return Size written or negative on error
     */
    fun resourceToStream(uri: String, stream: C2PAStream): Long =
        resourceToStream(nativeHandle, uri, stream.getNativeHandle())

    override fun close() {
        if (nativeHandle != 0L) {
            free(nativeHandle)
            nativeHandle = 0
        }
    }

    private external fun free(handle: Long)
    private external fun toJson(handle: Long): String
    private external fun resourceToStream(handle: Long, uri: String, streamHandle: Long): Long
}

/**
 * C2PA Builder for creating manifest stores
 */
class C2PABuilder private constructor(private var nativeHandle: Long) : Closeable {
    
    /**
     * Sign result containing size and optional manifest bytes
     */
    data class SignResult(val size: Long, val manifestBytes: ByteArray?)
    
    companion object {
        /**
         * Create a builder from JSON
         * @param manifestJson Manifest JSON string
         * @return Builder instance or null on error
         */
        @JvmStatic
        fun fromJson(manifestJson: String): C2PABuilder? {
            val handle = nativeFromJson(manifestJson)
            return if (handle != 0L) C2PABuilder(handle) else null
        }

        /**
         * Create a builder from an archive stream
         * @param stream Archive stream
         * @return Builder instance or null on error
         */
        @JvmStatic
        fun fromArchive(stream: C2PAStream): C2PABuilder? {
            val handle = nativeFromArchive(stream.getNativeHandle())
            return if (handle != 0L) C2PABuilder(handle) else null
        }

        @JvmStatic
        private external fun nativeFromJson(manifestJson: String): Long

        @JvmStatic
        private external fun nativeFromArchive(streamHandle: Long): Long
    }

    /**
     * Set the no-embed flag
     */
    fun setNoEmbed() = setNoEmbed(nativeHandle)

    /**
     * Set the remote URL
     * @param remoteUrl The remote URL
     * @return 0 on success, -1 on error
     */
    fun setRemoteUrl(remoteUrl: String): Int = setRemoteUrl(nativeHandle, remoteUrl)

    /**
     * Add a resource to the builder
     * @param uri Resource URI
     * @param stream Resource stream
     * @return 0 on success, -1 on error
     */
    fun addResource(uri: String, stream: C2PAStream): Int =
        addResource(nativeHandle, uri, stream.getNativeHandle())

    /**
     * Add an ingredient from a stream
     * @param ingredientJson Ingredient JSON
     * @param format Mime type or extension
     * @param source Source stream
     * @return 0 on success, -1 on error
     */
    fun addIngredientFromStream(
        ingredientJson: String,
        format: String,
        source: C2PAStream
    ): Int = addIngredientFromStream(nativeHandle, ingredientJson, format, source.getNativeHandle())

    /**
     * Write the builder to an archive
     * @param stream Destination stream
     * @return 0 on success, -1 on error
     */
    fun toArchive(stream: C2PAStream): Int = toArchive(nativeHandle, stream.getNativeHandle())

    /**
     * Sign and write the manifest
     * @param format Mime type or extension
     * @param source Source stream
     * @param dest Destination stream
     * @param signer Signer instance
     * @return Sign result with size and optional manifest bytes
     */
    fun sign(
        format: String,
        source: C2PAStream,
        dest: C2PAStream,
        signer: C2PASigner
    ): SignResult = sign(
        nativeHandle,
        format,
        source.getNativeHandle(),
        dest.getNativeHandle(),
        signer.getNativeHandle()
    )

    override fun close() {
        if (nativeHandle != 0L) {
            free(nativeHandle)
            nativeHandle = 0
        }
    }

    private external fun free(handle: Long)
    private external fun setNoEmbed(handle: Long)
    private external fun setRemoteUrl(handle: Long, remoteUrl: String): Int
    private external fun addResource(handle: Long, uri: String, streamHandle: Long): Int
    private external fun addIngredientFromStream(
        handle: Long,
        ingredientJson: String,
        format: String,
        sourceHandle: Long
    ): Int
    private external fun toArchive(handle: Long, streamHandle: Long): Int
    private external fun sign(
        handle: Long,
        format: String,
        sourceHandle: Long,
        destHandle: Long,
        signerHandle: Long
    ): SignResult
}

/**
 * Callback interface for custom signing operations
 */
interface SignCallback {
    fun sign(data: ByteArray): ByteArray?
}

/**
 * C2PA Signer for signing manifests
 */
class C2PASigner private constructor(private var nativeHandle: Long) : Closeable {
    
    companion object {
        /**
         * Create a signer from signer info
         * @param signerInfo Signer information
         * @return Signer instance or null on error
         */
        @JvmStatic
        fun fromInfo(signerInfo: SignerInfo): C2PASigner? {
            val handle = nativeFromInfo(signerInfo)
            return if (handle != 0L) C2PASigner(handle) else null
        }

        /**
         * Create a signer with a custom signing callback
         * @param algorithm Signing algorithm
         * @param certificateChain Certificate chain in PEM format
         * @param tsaURL Optional timestamp authority URL
         * @param callback Signing callback
         * @return Signer instance or null on error
         */
        @JvmStatic
        fun fromCallback(
            algorithm: String,
            certificateChain: String,
            tsaURL: String? = null,
            callback: SignCallback
        ): C2PASigner? {
            val handle = nativeFromCallback(algorithm, certificateChain, tsaURL, callback)
            return if (handle != 0L) C2PASigner(handle) else null
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
     * Get the native handle
     */
    internal fun getNativeHandle(): Long = nativeHandle

    /**
     * Get the reserve size for this signer
     * @return Reserve size or negative on error
     */
    fun reserveSize(): Long = reserveSize(nativeHandle)

    override fun close() {
        if (nativeHandle != 0L) {
            free(nativeHandle)
            nativeHandle = 0
        }
    }

    private external fun reserveSize(handle: Long): Long
    private external fun free(handle: Long)
}

/**
 * Memory-based C2PA stream implementation
 */
open class MemoryC2PAStream(initialData: ByteArray = ByteArray(0)) : C2PAStream() {
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

    override fun write(writeData: ByteArray, length: Long): Long {
        val bytesToWrite = minOf(length.toInt(), writeData.size)
        val newSize = maxOf(data.size, (position + bytesToWrite).toInt())
        
        if (newSize > data.size) {
            data = data.copyOf(newSize)
        }
        
        System.arraycopy(writeData, 0, data, position.toInt(), bytesToWrite)
        position += bytesToWrite
        
        return bytesToWrite.toLong()
    }

    override fun flush(): Long = 0L
}

/**
 * Example implementation of a file-based C2PA stream
 */
class FileC2PAStream(private val file: java.io.RandomAccessFile) : C2PAStream() {
    
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
}

/**
 * Stream utilities for creating different types of streams
 */
object StreamUtils {
    
    /**
     * Create a stream from Android raw resource
     */
    fun fromRawResource(context: android.content.Context, resourceId: Int): MemoryC2PAStream {
        val inputStream = context.resources.openRawResource(resourceId)
        val data = inputStream.readBytes()
        inputStream.close()
        return MemoryC2PAStream(data)
    }
    
    /**
     * Create a stream from asset
     */
    fun fromAsset(context: android.content.Context, assetPath: String): MemoryC2PAStream {
        val inputStream = context.assets.open(assetPath)
        val data = inputStream.readBytes()
        inputStream.close()
        return MemoryC2PAStream(data)
    }
    
    /**
     * Create a stream from file path
     */
    fun fromFile(filePath: String, mode: String = "r"): FileC2PAStream {
        return FileC2PAStream(java.io.RandomAccessFile(filePath, mode))
    }
    
    /**
     * Create a stream from byte array
     */
    fun fromByteArray(data: ByteArray): MemoryC2PAStream {
        return MemoryC2PAStream(data)
    }
    
    /**
     * Create a temporary file stream
     */
    fun createTempFile(context: android.content.Context, prefix: String, suffix: String): FileC2PAStream {
        val tempFile = java.io.File.createTempFile(prefix, suffix, context.cacheDir)
        return FileC2PAStream(java.io.RandomAccessFile(tempFile, "rw"))
    }
}

/**
 * Example usage functions
 */
object C2PAExamples {
    
    fun readManifestFromFile(filePath: String): String? {
        return C2PA.readFile(filePath)
    }
    
    fun signFile(
        sourcePath: String,
        destPath: String,
        manifestJson: String,
        certPath: String,
        keyPath: String
    ): String? {
        val cert = java.io.File(certPath).readText()
        val key = java.io.File(keyPath).readText()
        
        val signerInfo = SignerInfo(
            alg = "es256",
            signCert = cert,
            privateKey = key
        )
        
        return C2PA.signFile(sourcePath, destPath, manifestJson, signerInfo)
    }
    
    fun readManifestUsingStream(filePath: String, format: String): String? {
        FileC2PAStream(java.io.RandomAccessFile(filePath, "r")).use { stream ->
            C2PAReader.fromStream(format, stream)?.use { reader ->
                return reader.toJson()
            }
        }
        return null
    }
}
