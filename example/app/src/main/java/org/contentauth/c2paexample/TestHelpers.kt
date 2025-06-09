package org.contentauth.c2paexample

import org.contentauth.c2pa.SeekMode
import org.contentauth.c2pa.CallbackStream
import org.contentauth.c2pa.Stream
import java.io.ByteArrayOutputStream

/**
 * Memory stream implementation using CallbackStream
 */
class MemoryStream {
    private val buffer = ByteArrayOutputStream()
    private var position = 0
    private var data: ByteArray
    
    val stream: Stream
    
    constructor() {
        data = ByteArray(0)
        stream = createStream()
    }
    
    constructor(initialData: ByteArray) {
        buffer.write(initialData)
        data = buffer.toByteArray()
        stream = createStream()
    }
    
    private fun createStream(): Stream {
        return CallbackStream(
            reader = { buffer, length ->
                if (position >= data.size) return@CallbackStream 0
                val toRead = minOf(length, data.size - position)
                System.arraycopy(data, position, buffer, 0, toRead)
                position += toRead
                toRead
            },
            seeker = { offset, mode ->
                position = when (mode) {
                    SeekMode.START -> offset.toInt()
                    SeekMode.CURRENT -> position + offset.toInt()
                    SeekMode.END -> data.size + offset.toInt()
                }
                position = position.coerceIn(0, data.size)
                position.toLong()
            },
            writer = { writeData, length ->
                if (position < data.size) {
                    // Writing in the middle - need to handle carefully
                    val newData = data.toMutableList()
                    for (i in 0 until length) {
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
                    buffer.write(writeData, 0, length)
                    data = buffer.toByteArray()
                }
                position += length
                length
            },
            flusher = {
                data = buffer.toByteArray()
                0
            }
        )
    }
    
    fun seek(offset: Long, mode: Int): Long = stream.seek(offset, mode)
    fun close() = stream.close()
    fun getData(): ByteArray = data
}

/**
 * Helper object for creating web service signers
 */
object WebServiceSignerHelper {
    fun createWebServiceSigner(
        serviceUrl: String,
        algorithm: org.contentauth.c2pa.SigningAlgorithm,
        certsPem: String,
        tsaUrl: String? = null
    ): org.contentauth.c2pa.Signer {
        // Check if this is a mock service URL
        val mockService = MockSigningService.getService(serviceUrl)
        if (mockService != null) {
            // Return a callback signer that uses the mock service
            return org.contentauth.c2pa.Signer.withCallback(algorithm, certsPem, tsaUrl) { data ->
                mockService.handleRequest("test-request", data)
            }
        }
        
        // For real web service, you would implement actual HTTP calls here
        // For now, throw an exception to indicate this is not implemented
        throw UnsupportedOperationException("Real web service signing not implemented in test environment")
    }
}