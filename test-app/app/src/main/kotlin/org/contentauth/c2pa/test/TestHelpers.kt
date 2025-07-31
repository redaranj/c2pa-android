package org.contentauth.c2pa.test

import org.contentauth.c2pa.*
import java.io.ByteArrayOutputStream

/**
 * Memory stream implementation using CallbackStream
 * (Copied from library's InstrumentedTests.kt)
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
 * Mock signing service for Android testing (avoids socket permissions)
 * (Copied from library's InstrumentedTests.kt)
 */
class MockSigningService(
    private val signingFunction: (ByteArray) -> ByteArray
) {
    fun handleRequest(requestId: String, data: ByteArray): ByteArray {
        // Simulate async processing
        return signingFunction(data)
    }
    
    companion object {
        private val activeServices = mutableMapOf<String, MockSigningService>()
        
        fun register(url: String, service: MockSigningService) {
            activeServices[url] = service
        }
        
        fun unregister(url: String) {
            activeServices.remove(url)
        }
        
        fun getService(url: String): MockSigningService? {
            return activeServices[url]
        }
    }
}

/**
 * Helper object for creating web service signers
 */
object WebServiceSignerHelper {
    fun createWebServiceSigner(
        serviceUrl: String,
        algorithm: SigningAlgorithm,
        certsPem: String,
        tsaUrl: String? = null
    ): Signer {
        // Use MockSigningService for the actual signing
        val mockService = MockSigningService.getService(serviceUrl)
            ?: throw IllegalStateException("No mock service registered for $serviceUrl")
        
        return Signer.withWebService(
            algorithm = algorithm,
            certificateChainPEM = certsPem,
            tsaURL = tsaUrl,
            signer = { data -> mockService.handleRequest("test", data) }
        )
    }
}