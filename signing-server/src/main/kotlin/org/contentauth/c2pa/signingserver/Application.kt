package org.contentauth.c2pa.signingserver

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.contentauth.c2pa.*

// Minimal test server - mirrors iOS implementation
fun main() {
    // Load the native libraries from the compiled location
    val workingDir = System.getProperty("user.dir")
    println("[C2PA Server] Working directory: $workingDir")
    
    // First load the C2PA C library
    val c2paLibFile = java.io.File(workingDir, "libs/libc2pa_c.dylib")
    if (c2paLibFile.exists()) {
        System.load(c2paLibFile.absolutePath)
        println("[C2PA Server] Loaded C2PA C library from: ${c2paLibFile.absolutePath}")
    }
    
    // Then load the JNI library
    val jniLibFile = java.io.File(workingDir, "libs/libc2pa_server_jni.dylib")
    if (jniLibFile.exists()) {
        System.load(jniLibFile.absolutePath)
        println("[C2PA Server] Loaded JNI library from: ${jniLibFile.absolutePath}")
    } else {
        throw RuntimeException("Cannot find JNI library at: ${jniLibFile.absolutePath}")
    }
    embeddedServer(
        Netty, 
        port = 8080,
        host = "0.0.0.0"
    ) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        
        routing {
            // Simple C2PA signing endpoint matching iOS
            post("/api/v1/c2pa/sign") {
                println("[C2PA Server] Received signing request")
                try {
                    val multipart = call.receiveMultipart()
                    var requestJson: String? = null
                    var imageData: ByteArray? = null
                    
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                when (part.name) {
                                    "request" -> {
                                        requestJson = part.streamProvider().readBytes().decodeToString()
                                        println("[C2PA Server] Received request JSON: $requestJson")
                                    }
                                    "image" -> {
                                        imageData = part.streamProvider().readBytes()
                                        println("[C2PA Server] Received image: ${imageData?.size} bytes")
                                    }
                                }
                            }
                            else -> {}
                        }
                        part.dispose()
                    }
                    
                    requireNotNull(requestJson) { "Missing request JSON" }
                    requireNotNull(imageData) { "Missing image data" }
                    
                    // Parse the request JSON to get manifestJSON and format
                    val requestObj = Json.decodeFromString<Map<String, String>>(requestJson!!)
                    val manifestJSON = requestObj["manifestJSON"] ?: throw IllegalArgumentException("Missing manifestJSON in request")
                    val format = requestObj["format"] ?: throw IllegalArgumentException("Missing format in request")
                    
                    // Sign with test certificates (same as iOS)
                    val signedData = signWithTestCerts(
                        manifestJSON, 
                        imageData!!, 
                        format
                    )
                    
                    call.respondBytes(
                        signedData,
                        ContentType.parse(format!!)
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to e.message)
                    )
                }
            }
            
            // Health check
            get("/health") {
                call.respond(mapOf("status" to "ok", "version" to "1.0.0"))
            }
        }
    }.start(wait = true)
}

private fun signWithTestCerts(
    manifestJSON: String,
    imageData: ByteArray, 
    format: String
): ByteArray {
    // Load test certificates from resources
    val certificateChain = Thread.currentThread().contextClassLoader
        .getResourceAsStream("certs/es256_certs.pem")?.reader()?.readText()
        ?: throw IllegalStateException("Cannot load certificate")
    val privateKey = Thread.currentThread().contextClassLoader
        .getResourceAsStream("certs/es256_private.key")?.reader()?.readText()
        ?: throw IllegalStateException("Cannot load private key")
    
    println("[C2PA Server] Creating signer with manifest JSON:")
    println(manifestJSON.take(200) + if (manifestJSON.length > 200) "..." else "")
    println("[C2PA Server] Certificate chain length: ${certificateChain.length}")
    println("[C2PA Server] Image size: ${imageData.size} bytes, format: $format")
    
    // Use the C2PA API with Signer
    val signer = Signer.fromKeys(
        certsPEM = certificateChain,
        privateKeyPEM = privateKey,
        algorithm = SigningAlgorithm.ES256,
        tsaURL = null
    )
    
    val builder = Builder.fromJson(manifestJSON)
    
    // Use MemoryStream like the tests do
    val sourceStream = MemoryStream(imageData)
    val destStream = MemoryStream()
    
    try {
        val result = builder.sign(
            format = format.substringAfter('/'), // "jpeg" from "image/jpeg"  
            source = sourceStream.stream,
            dest = destStream.stream,
            signer = signer
        )
        
        println("[C2PA Server] Signing completed, size: ${result.size}")
        // Return the signed data
        return destStream.getData()
        
    } catch (e: Exception) {
        println("[C2PA Server] Signing failed: ${e.message}")
        throw e
    } finally {
        signer.close()
        builder.close()
    }
}