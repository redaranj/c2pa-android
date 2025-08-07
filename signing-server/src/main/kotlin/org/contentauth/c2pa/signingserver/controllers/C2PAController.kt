package org.contentauth.c2pa.signingserver.controllers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import org.contentauth.c2pa.signingserver.models.C2PASigningRequest
import org.contentauth.c2pa.signingserver.services.C2PASigningService

class C2PAController(
    private val c2paService: C2PASigningService
) {
    
    suspend fun signManifest(call: ApplicationCall) {
        try {
            // Check content type for multipart
            val contentType = call.request.contentType()
            if (contentType.match(ContentType.MultiPart.FormData)) {
                handleMultipartSignRequest(call)
            } else if (contentType.match(ContentType.Application.Json)) {
                handleJsonSignRequest(call)
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Content-Type must be multipart/form-data or application/json")
                )
            }
        } catch (e: Exception) {
            call.application.log.error("Error signing manifest", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Failed to sign manifest"))
            )
        }
    }
    
    private suspend fun handleMultipartSignRequest(call: ApplicationCall) {
        val multipart = call.receiveMultipart()
        var imageData: ByteArray? = null
        var signingRequest: C2PASigningRequest? = null
        
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    if (part.name == "image") {
                        imageData = part.streamProvider().readBytes()
                    }
                }
                is PartData.FormItem -> {
                    if (part.name == "request") {
                        val json = part.value
                        signingRequest = Json.decodeFromString(C2PASigningRequest.serializer(), json)
                    }
                }
                else -> {}
            }
            part.dispose()
        }
        
        if (imageData == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing image data")
            )
            return
        }
        
        if (signingRequest == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing signing request")
            )
            return
        }
        
        // Sign the manifest
        val response = c2paService.signManifest(
            manifestJSON = signingRequest!!.manifestJSON,
            imageData = imageData!!,
            format = signingRequest!!.format
        )
        
        // Return the signed image with manifest embedded
        call.respondBytes(
            response.manifestStore,
            ContentType.parse(signingRequest!!.format),
            HttpStatusCode.OK
        )
    }
    
    private suspend fun handleJsonSignRequest(call: ApplicationCall) {
        // For JSON requests, we expect base64-encoded image data
        val request = call.receive<Map<String, Any>>()
        
        val manifestJSON = request["manifestJSON"] as? String
            ?: throw IllegalArgumentException("Missing manifestJSON")
        
        val format = request["format"] as? String
            ?: throw IllegalArgumentException("Missing format")
        
        val imageBase64 = request["imageData"] as? String
            ?: throw IllegalArgumentException("Missing imageData")
        
        val imageData = try {
            java.util.Base64.getDecoder().decode(imageBase64)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid base64 image data")
        }
        
        // Sign the manifest
        val response = c2paService.signManifest(
            manifestJSON = manifestJSON,
            imageData = imageData,
            format = format
        )
        
        // Return the response as JSON with base64-encoded manifest store
        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "manifestStore" to java.util.Base64.getEncoder().encodeToString(response.manifestStore),
                "signatureInfo" to response.signatureInfo
            )
        )
    }
}