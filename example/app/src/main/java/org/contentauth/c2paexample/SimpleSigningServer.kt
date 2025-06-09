package org.contentauth.c2paexample

import org.contentauth.c2pa.*
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * A minimal HTTP server for testing web service signers without external dependencies
 * This is equivalent to iOS SimpleSigningServer.swift
 * 
 * Uses a simple HTTP server to handle POST /sign requests and returns signatures
 */
class SimpleSigningServer(
    private val signingFunction: (ByteArray) -> ByteArray,
    private val port: Int = 0  // 0 means let the system assign a port
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    
    /**
     * Start the server and return the actual port number
     */
    @Throws(IOException::class)
    fun start(): Int {
        if (isRunning.get()) {
            throw IllegalStateException("Server is already running")
        }
        
        // Create server socket with automatic port assignment if port is 0
        val socket = ServerSocket(port)
        socket.reuseAddress = true
        serverSocket = socket
        val actualPort = socket.localPort
        
        isRunning.set(true)
        
        // Start accepting connections in a background thread
        thread(isDaemon = true, name = "SimpleSigningServer-${actualPort}") {
            while (isRunning.get() && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    // Handle each client in a separate thread
                    executor.execute {
                        handleConnection(client)
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        println("Error accepting connection: ${e.message}")
                    }
                }
            }
        }
        
        return actualPort
    }
    
    /**
     * Stop the server
     */
    fun stop() {
        isRunning.set(false)
        serverSocket?.close()
        serverSocket = null
        executor.shutdown()
    }
    
    /**
     * Handle a client connection
     */
    private fun handleConnection(client: Socket) {
        client.use { socket ->
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                
                // Buffer to accumulate request data
                val requestBuffer = mutableListOf<Byte>()
                val buffer = ByteArray(1024)
                
                // Read until we have the complete request
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    
                    requestBuffer.addAll(buffer.take(bytesRead).toList())
                    
                    // Check if we have the complete headers (look for \r\n\r\n)
                    val requestBytes = requestBuffer.toByteArray()
                    val requestString = String(requestBytes)
                    val headerEndIndex = requestString.indexOf("\r\n\r\n")
                    
                    if (headerEndIndex != -1) {
                        // Parse Content-Length from headers
                        val headers = requestString.substring(0, headerEndIndex)
                        val contentLength = parseContentLength(headers)
                        
                        // Check if we have the complete body
                        val bodyStartIndex = headerEndIndex + 4
                        val bodyLength = requestBytes.size - bodyStartIndex
                        
                        if (bodyLength >= contentLength) {
                            // We have the complete request
                            val body = requestBytes.sliceArray(bodyStartIndex until bodyStartIndex + contentLength)
                            processRequest(headers, body, output)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error handling connection: ${e.message}")
            }
        }
    }
    
    /**
     * Parse Content-Length from headers
     */
    private fun parseContentLength(headers: String): Int {
        val lines = headers.split("\r\n")
        for (line in lines) {
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                return line.substring(15).trim().toInt()
            }
        }
        return 0
    }
    
    /**
     * Process the HTTP request and send response
     */
    private fun processRequest(headers: String, body: ByteArray, output: java.io.OutputStream) {
        val lines = headers.split("\r\n")
        val requestLine = lines.firstOrNull() ?: ""
        
        // Parse method and path
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0) ?: ""
        val path = parts.getOrNull(1) ?: ""
        
        if (method == "POST" && path == "/sign") {
            // Sign the data
            val signature = signData(body)
            
            // Send success response
            val response = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: application/octet-stream\r\n")
                append("Content-Length: ${signature.size}\r\n")
                append("\r\n")
            }
            
            output.write(response.toByteArray())
            output.write(signature)
            output.flush()
        } else {
            // Send 404 response
            val errorBody = "Endpoint not found"
            val response = buildString {
                append("HTTP/1.1 404 Not Found\r\n")
                append("Content-Type: text/plain\r\n")
                append("Content-Length: ${errorBody.length}\r\n")
                append("\r\n")
                append(errorBody)
            }
            
            output.write(response.toByteArray())
            output.flush()
        }
    }
    
    /**
     * Sign data using the provided signing function
     * This matches the iOS implementation
     */
    private fun signData(data: ByteArray): ByteArray {
        return try {
            signingFunction(data)
        } catch (e: Exception) {
            println("Error signing data: ${e.message}")
            ByteArray(64) // Return empty signature on error
        }
    }
    
    companion object {
        /**
         * Create a test signing server with the provided certificates and key
         * This matches the iOS createTestSigningServer method
         */
        fun createTestSigningServer(
            certsPem: String,
            privateKeyPem: String,
            algorithm: SigningAlgorithm = SigningAlgorithm.ES256
        ): Pair<SimpleSigningServer, String> {
            // Create a server with a signing function that uses SigningHelper
            val signingFunction: (ByteArray) -> ByteArray = { data ->
                SigningHelper.signWithPEMKey(data, privateKeyPem, algorithm.name.uppercase())
            }
            
            val server = SimpleSigningServer(signingFunction)
            return Pair(server, certsPem)
        }
        
    }
}

/**
 * Mock signing service for Android testing (avoids socket permissions)
 */
class MockSigningService(
    private val signingFunction: (ByteArray) -> ByteArray
) {
    private val requests = ConcurrentHashMap<String, ByteArray>()
    
    fun handleRequest(requestId: String, data: ByteArray): ByteArray {
        // Simulate async processing
        return signingFunction(data)
    }
    
    companion object {
        private val activeServices = ConcurrentHashMap<String, MockSigningService>()
        
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
 * Extension to create a web service signer that communicates with a signing server
 * On Android, uses MockSigningService for testing to avoid socket permissions
 */
fun Signer.Companion.webServiceSigner(
    serviceUrl: String,
    algorithm: SigningAlgorithm,
    certsPem: String,
    tsaUrl: String? = null
): Signer {
    return Signer.withCallback(algorithm, certsPem, tsaUrl) { data ->
        // For testing on Android, check if we have a mock service registered
        val mockService = MockSigningService.getService(serviceUrl)
        if (mockService != null) {
            // Use mock service for testing
            return@withCallback mockService.handleRequest(UUID.randomUUID().toString(), data)
        }
        
        // Make real HTTP POST request to the signing service
        try {
            val url = java.net.URL(serviceUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("Content-Length", data.size.toString())
            
            // Send data
            connection.outputStream.use { output ->
                output.write(data)
            }
            
            // Read response
            if (connection.responseCode == 200) {
                connection.inputStream.use { input ->
                    input.readBytes()
                }
            } else {
                throw RuntimeException("Signing service returned ${connection.responseCode}")
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to call signing service: ${e.message}", e)
        }
    }
}