package org.contentauth.c2pa.signingserver.controllers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.contentauth.c2pa.signingserver.models.CertificateSigningRequest
import org.contentauth.c2pa.signingserver.services.CertificateSigningService

class CertificateController(
    private val certificateService: CertificateSigningService
) {
    
    suspend fun signCSR(call: ApplicationCall) {
        try {
            val csrRequest = call.receive<CertificateSigningRequest>()
            
            // Validate CSR format
            if (!csrRequest.csr.contains("BEGIN CERTIFICATE REQUEST")) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid CSR format")
                )
                return
            }
            
            // Sign the CSR
            val response = certificateService.signCSR(
                csrPEM = csrRequest.csr,
                metadata = csrRequest.metadata
            )
            
            // Log the issuance
            call.application.log.info("Issued certificate: ${response.certificateId}")
            
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            call.application.log.error("Error signing CSR", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Failed to sign CSR"))
            )
        }
    }
}