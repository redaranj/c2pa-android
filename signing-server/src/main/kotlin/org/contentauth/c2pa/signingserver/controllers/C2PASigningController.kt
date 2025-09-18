package org.contentauth.c2pa.signingserver.controllers

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.SigningAlgorithm
import org.contentauth.c2pa.signingserver.models.C2PASigningRequest
import org.contentauth.c2pa.signingserver.models.C2PASigningResponse
import java.io.File
import java.util.Base64

class C2PASigningController {
    suspend fun signManifest(call: ApplicationCall) {
        try {
            val signingRequest = call.receive<C2PASigningRequest>()

            call.application.log.info("[C2PA Controller] Received signing request")

            val certPath = "${System.getProperty("user.dir")}/Resources/es256_certs.pem"
            val keyPath = "${System.getProperty("user.dir")}/Resources/es256_private.key"

            val certificateChain = File(certPath).readText()
            val privateKeyPEM = File(keyPath).readText()

            if (!certificateChain.contains("BEGIN CERTIFICATE")) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Invalid certificate format")
                )
                return
            }

            if (!privateKeyPEM.contains("BEGIN PRIVATE KEY") && !privateKeyPEM.contains("BEGIN EC PRIVATE KEY")) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Invalid private key format")
                )
                return
            }

            // Decode the base64-encoded data to sign
            val dataToSign = try {
                Base64.getDecoder().decode(signingRequest.dataToSign)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid base64-encoded data")
                )
                return
            }

            call.application.log.info("[C2PA] Creating signer for data signing")
            call.application.log.info("[C2PA] Data to sign size: ${dataToSign.size} bytes")
            call.application.log.info("[C2PA] Using certificates from: Resources/es256_certs.pem")

            // Use C2PA library's Ed25519 signing if available, otherwise fall back to ES256
            val signatureBytes = if (privateKeyPEM.contains("ED25519")) {
                // Use C2PA's Ed25519 signing
                C2PA.ed25519Sign(dataToSign, privateKeyPEM)
            } else {
                // For ES256, we still need to use Java crypto as C2PA expects full manifest signing
                // Extract the private key from PEM
                val privateKeyContent = privateKeyPEM
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----BEGIN EC PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----END EC PRIVATE KEY-----", "")
                    .replace("\n", "")
                    .replace("\r", "")

                val keyBytes = Base64.getDecoder().decode(privateKeyContent)
                val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
                val keyFactory = java.security.KeyFactory.getInstance("EC")
                val privateKey = keyFactory.generatePrivate(keySpec)

                // Sign the data using ECDSA with SHA-256
                val signature = java.security.Signature.getInstance("SHA256withECDSA")
                signature.initSign(privateKey)
                signature.update(dataToSign)
                signature.sign()
            }

            val base64Signature = Base64.getEncoder().encodeToString(signatureBytes)

            call.application.log.info("[C2PA] Signature created, size: ${signatureBytes.size} bytes")
            call.application.log.info("[C2PA Controller] Signature generated successfully")

            val response = C2PASigningResponse(
                signature = base64Signature
            )

            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            call.application.log.error("Error signing manifest", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Failed to sign manifest"))
            )
        }
    }
}
