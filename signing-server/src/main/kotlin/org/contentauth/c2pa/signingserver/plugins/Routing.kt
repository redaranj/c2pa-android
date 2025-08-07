package org.contentauth.c2pa.signingserver.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.contentauth.c2pa.signingserver.controllers.CertificateController
import org.contentauth.c2pa.signingserver.controllers.C2PAController

fun Application.configureRouting() {
    val certificateService = attributes[org.contentauth.c2pa.signingserver.plugins.CertificateServiceKey]
    val c2paService = attributes[org.contentauth.c2pa.signingserver.plugins.C2PAServiceKey]
    
    val certificateController = CertificateController(certificateService)
    val c2paController = C2PAController(c2paService)
    
    routing {
        // Root endpoint
        get("/") {
            call.respond(
                mapOf(
                    "status" to "C2PA Signing Server is running",
                    "version" to "1.0.0",
                    "mode" to "testing",
                    "c2pa_version" to "mock-0.1.0"
                )
            )
        }
        
        // Health check
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }
        
        // API v1 routes
        route("/api/v1") {
            // Certificate endpoints
            route("/certificates") {
                post("/sign") {
                    certificateController.signCSR(call)
                }
            }
            
            // C2PA signing endpoints
            route("/c2pa") {
                post("/sign") {
                    c2paController.signManifest(call)
                }
            }
        }
    }
}