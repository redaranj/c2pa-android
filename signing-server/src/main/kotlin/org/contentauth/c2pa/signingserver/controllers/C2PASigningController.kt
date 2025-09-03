package org.contentauth.c2pa.signingserver.controllers

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import kotlinx.serialization.json.Json
import org.contentauth.c2pa.signingserver.services.C2PASigningService

class C2PASigningController(
    private val c2paService: C2PASigningService
) {
    suspend fun signManifest(call: ApplicationCall) {
        try {
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
        var requestJson: String? = null
        var imageData: ByteArray? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    when (part.name) {
                        "request" -> {
                            requestJson = part.streamProvider().readBytes().decodeToString()
                        }

                        "image" -> {
                            imageData = part.streamProvider().readBytes()
                        }
                    }
                }

                else -> {}
            }
            part.dispose()
        }

        requireNotNull(requestJson) { "Missing request JSON" }
        requireNotNull(imageData) { "Missing image data" }

        val requestObj = Json.decodeFromString<Map<String, String>>(requestJson!!)
        val manifestJSON = requestObj["manifestJSON"]
            ?: throw IllegalArgumentException("Missing manifestJSON in request")
        val format =
            requestObj["format"] ?: throw IllegalArgumentException("Missing format in request")

        val response = c2paService.signManifest(
            manifestJSON = manifestJSON,
            imageData = imageData!!,
            format = format
        )

        call.respondBytes(
            response.manifestStore,
            ContentType.parse(format),
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
                "manifestStore" to java.util.Base64.getEncoder()
                    .encodeToString(response.manifestStore),
                "signatureInfo" to response.signatureInfo
            )
        )
    }
}
