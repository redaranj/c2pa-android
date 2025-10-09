package org.contentauth.c2pa

import java.io.Closeable

/**
 * C2PA Reader for reading manifest stores
 */
class Reader internal constructor(private var ptr: Long) : Closeable {

    companion object {
        init {
            loadC2PALibraries()
        }

        /**
         * Create a reader from a stream
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromStream(format: String, stream: Stream): Reader =
            executeC2PAOperation("Failed to create reader from stream") {
                val handle = fromStreamNative(format, stream.rawPtr)
                if (handle == 0L) null else Reader(handle)
            }

        /**
         * Create a reader from manifest data and stream
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromManifestAndStream(format: String, stream: Stream, manifest: ByteArray): Reader =
            executeC2PAOperation("Failed to create reader from manifest and stream") {
                val handle = fromManifestDataAndStreamNative(format, stream.rawPtr, manifest)
                if (handle == 0L) null else Reader(handle)
            }

        @JvmStatic
        private external fun fromStreamNative(format: String, streamHandle: Long): Long

        @JvmStatic
        private external fun fromManifestDataAndStreamNative(
            format: String,
            streamHandle: Long,
            manifestData: ByteArray,
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
