package org.contentauth.c2paexample

import org.contentauth.c2pa.*
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.Signature
import java.util.Base64
import java.util.concurrent.Executors

/**
 * A minimal HTTP server for testing web service signers without external dependencies
 * This is equivalent to iOS SimpleSigningServer.swift
 */
class SimpleSigningServer(
    private val algorithm: SigningAlgorithm,
    private val privateKeyPem: String,
    private val port: Int = 0
) {
    private var serverSocket: ServerSocket? = null
    private var actualPort: Int = 0

    @Throws(IOException::class)
    fun start(): Int {
        val socket = ServerSocket(port)
        actualPort = socket.localPort
        serverSocket = socket
        
        // Start a thread to handle connections
        Executors.newCachedThreadPool().execute {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    handleClient(client)
                } catch (e: Exception) {
                    // Server stopped
                }
            }
        }
        
        return actualPort
    }

    fun stop() {
        serverSocket?.close()
        serverSocket = null
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            val input = socket.getInputStream().bufferedReader()
            val output = socket.getOutputStream()
            
            // Read HTTP request
            val requestLine = input.readLine()
            if (!requestLine.startsWith("POST /sign")) {
                val response = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
                output.write(response.toByteArray())
                return
            }
            
            // Read headers
            var contentLength = 0
            while (true) {
                val line = input.readLine()
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:")) {
                    contentLength = line.substring(15).trim().toInt()
                }
            }
            
            // Read body
            val body = ByteArray(contentLength)
            var bytesRead = 0
            while (bytesRead < contentLength) {
                val read = socket.getInputStream().read(body, bytesRead, contentLength - bytesRead)
                if (read == -1) break
                bytesRead += read
            }
            
            // Sign data
            val signature = signData(body)
            
            // Send response
            val response = "HTTP/1.1 200 OK\r\n" +
                          "Content-Type: application/octet-stream\r\n" +
                          "Content-Length: ${signature.size}\r\n" +
                          "\r\n"
            output.write(response.toByteArray())
            output.write(signature)
            output.flush()
        }
    }

    private fun signData(data: ByteArray): ByteArray {
        return try {
            // Use the real signing based on the algorithm
            when (algorithm) {
                SigningAlgorithm.es256, SigningAlgorithm.es384, SigningAlgorithm.es512 -> {
                    // For ECDSA algorithms, we need to use proper signing
                    signWithPrivateKey(data, privateKeyPem, algorithm)
                }
                SigningAlgorithm.ps256, SigningAlgorithm.ps384, SigningAlgorithm.ps512 -> {
                    // For RSA PSS algorithms
                    signWithPrivateKey(data, privateKeyPem, algorithm)
                }
                SigningAlgorithm.ed25519 -> {
                    // For Ed25519, use the C2PA library's ed25519Sign method
                    C2PA.ed25519Sign(data, privateKeyPem) ?: ByteArray(64)
                }
            }
        } catch (e: Exception) {
            // Return empty signature on error
            ByteArray(64)
        }
    }
    
    private fun signWithPrivateKey(data: ByteArray, privateKeyPem: String, algorithm: SigningAlgorithm): ByteArray {
        // Parse the private key from PEM
        val privateKeyStr = privateKeyPem
            .replace("-----BEGIN EC PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END EC PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")
            .trim()
        
        val keyBytes = Base64.getDecoder().decode(privateKeyStr)
        
        // Determine the key type and algorithm
        val (javaAlgorithm, keyAlgorithm) = when (algorithm) {
            SigningAlgorithm.es256 -> "SHA256withECDSA" to "EC"
            SigningAlgorithm.es384 -> "SHA384withECDSA" to "EC"
            SigningAlgorithm.es512 -> "SHA512withECDSA" to "EC"
            SigningAlgorithm.ps256 -> "SHA256withRSA/PSS" to "RSA"
            SigningAlgorithm.ps384 -> "SHA384withRSA/PSS" to "RSA"
            SigningAlgorithm.ps512 -> "SHA512withRSA/PSS" to "RSA"
            SigningAlgorithm.ed25519 -> throw IllegalArgumentException("Use C2PA.ed25519Sign for Ed25519")
        }
        
        // Create the private key
        val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = java.security.KeyFactory.getInstance(keyAlgorithm)
        val privateKey = keyFactory.generatePrivate(keySpec)
        
        // Sign the data
        val signature = Signature.getInstance(javaAlgorithm)
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }


    companion object {
        fun createTestSigningServer(
            certsPem: String,
            privateKeyPem: String,
            algorithm: SigningAlgorithm = SigningAlgorithm.es256
        ): Pair<SimpleSigningServer, String> {
            val server = SimpleSigningServer(algorithm, privateKeyPem)
            return Pair(server, certsPem)
        }
    }
}

/**
 * Helper class for creating web service signers
 */
object WebServiceSignerHelper {
    
    fun createWebServiceSigner(
        serviceUrl: String,
        algorithm: SigningAlgorithm,
        certsPem: String
    ): Signer {
        // Create a signer with a callback that calls the web service
        return Signer(algorithm, certsPem, null) { data ->
            // In production, this would make an HTTP POST to serviceUrl
            // For testing, we'll simulate a response
            callSigningService(serviceUrl, data)
        }
    }
    
    private fun callSigningService(serviceUrl: String, data: ByteArray): ByteArray {
        // Mock implementation - in production would make HTTP call
        return ByteArray(64) { it.toByte() }
    }
}