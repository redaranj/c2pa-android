package org.contentauth.c2pa

import java.io.Closeable

/**
 * C2PA Builder for creating manifest stores
 */
class Builder internal constructor(private var ptr: Long) : Closeable {

    /**
     * Sign result containing size and optional manifest bytes
     */
    data class SignResult(val size: Long, val manifestBytes: ByteArray?)

    companion object {
        init {
            loadC2PALibraries()
        }

        /**
         * Create a builder from JSON
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromJson(manifestJSON: String): Builder = executeC2PAOperation("Failed to create builder from JSON") {
            val handle = nativeFromJson(manifestJSON)
            if (handle == 0L) null else Builder(handle)
        }

        /**
         * Create a builder from an archive stream
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromArchive(archive: Stream): Builder = executeC2PAOperation("Failed to create builder from archive") {
            val handle = nativeFromArchive(archive.rawPtr)
            if (handle == 0L) null else Builder(handle)
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
    fun signDataHashedEmbeddable(signer: Signer, dataHash: String, format: String, asset: Stream? = null): ByteArray {
        val result = signDataHashedEmbeddableNative(
            ptr,
            signer.ptr,
            dataHash,
            format,
            asset?.rawPtr ?: 0L,
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
        sourceHandle: Long,
    ): Int
    private external fun toArchiveNative(handle: Long, streamHandle: Long): Int
    private external fun signNative(
        handle: Long,
        format: String,
        sourceHandle: Long,
        destHandle: Long,
        signerHandle: Long,
    ): SignResult
    private external fun dataHashedPlaceholderNative(handle: Long, reservedSize: Long, format: String): ByteArray?
    private external fun signDataHashedEmbeddableNative(
        handle: Long,
        signerHandle: Long,
        dataHash: String,
        format: String,
        assetHandle: Long,
    ): ByteArray?
}
