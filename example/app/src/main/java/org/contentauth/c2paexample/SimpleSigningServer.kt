package org.contentauth.c2paexample

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.contentauth.c2pa.*
import java.io.IOException
import java.net.InetSocketAddress
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.Executors

/**
 * A minimal HTTP server for testing web service signers without external dependencies
 * This is equivalent to iOS SimpleSigningServer.swift
 */
class SimpleSigningServer(
    private val signer: C2PASigner,
    private val port: Int = 0
) {
    private var server: HttpServer? = null
    private var actualPort: Int = 0

    @Throws(IOException::class)
    fun start(): Int {
        val httpServer = HttpServer.create(InetSocketAddress(port), 0)
        httpServer.executor = Executors.newCachedThreadPool()
        
        httpServer.createContext("/sign", SignHandler())
        
        httpServer.start()
        actualPort = httpServer.address.port
        server = httpServer
        
        return actualPort
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private inner class SignHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                if (exchange.requestMethod != "POST") {
                    sendErrorResponse(exchange, 404, "Endpoint not found")
                    return
                }

                val requestBody = exchange.requestBody.readBytes()
                val signature = signData(requestBody)

                exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                exchange.sendResponseHeaders(200, signature.size.toLong())
                exchange.responseBody.use { output ->
                    output.write(signature)
                }
            } catch (e: Exception) {
                sendErrorResponse(exchange, 500, e.message ?: "Internal server error")
            }
        }
    }

    private fun signData(data: ByteArray): ByteArray {
        return signer.sign(data)
    }

    private fun sendErrorResponse(exchange: HttpExchange, code: Int, message: String) {
        val response = message.toByteArray()
        exchange.responseHeaders.add("Content-Type", "text/plain")
        exchange.sendResponseHeaders(code, response.size.toLong())
        exchange.responseBody.use { output ->
            output.write(response)
        }
    }

    companion object {
        fun createTestSigningServer(
            certsPem: String,
            privateKeyPem: String
        ): Pair<SimpleSigningServer, String> {
            val signerInfo = SignerInfo("es256", certsPem, privateKeyPem)
            val signer = C2PASigner.fromInfo(signerInfo)
                ?: throw IllegalStateException("Could not create signer")
            
            val server = SimpleSigningServer(signer)
            return Pair(server, certsPem)
        }
    }
}

/**
 * Extension class for web service signing demonstration
 */
class WebServiceSigner(
    private val serviceUrl: String,
    private val algorithm: String,
    private val certsPem: String
) : C2PASigner() {
    
    override fun sign(data: ByteArray): ByteArray {
        // In production, this would make an HTTP POST to serviceUrl
        // For testing, we'll simulate a response
        return ByteArray(64) { it.toByte() } // Mock signature
    }
    
    override fun getAlg(): String = algorithm
    
    override fun getCerts(): String = certsPem
    
    override fun getReserveSize(): Long = 10000L
}