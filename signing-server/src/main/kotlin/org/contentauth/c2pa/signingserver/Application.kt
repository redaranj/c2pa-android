package org.contentauth.c2pa.signingserver

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.contentauth.c2pa.c2paVersion
import org.contentauth.c2pa.signingserver.controllers.C2PAConfigurationController
import org.contentauth.c2pa.signingserver.controllers.C2PASigningController
import org.contentauth.c2pa.signingserver.controllers.CertificateSigningController
import org.contentauth.c2pa.signingserver.middleware.checkBearerAuth
import org.contentauth.c2pa.signingserver.services.CertificateSigningService

fun main() {
    val certificateSigningService = CertificateSigningService()
    
    val c2paSigningController = C2PASigningController()
    val c2paConfigurationController = C2PAConfigurationController()
    val certificateSigningController = CertificateSigningController(certificateSigningService)

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                }
            )
        }

        routing {
            // Root endpoint matching iOS
            get {
                call.respond(
                    mapOf(
                        "status" to "C2PA Signing Server is running",
                        "version" to "1.0.0",
                        "mode" to "testing",
                        "c2pa_version" to c2paVersion
                    )
                )
            }

            // Health check endpoint
            get("/health") {
                call.respond(HttpStatusCode.OK)
            }

            // API v1 routes
            route("/api/v1") {
                // Certificate signing endpoint
                route("/certificates") {
                    post("/sign") { certificateSigningController.signCSR(call) }
                }

                // C2PA endpoints with bearer auth protection
                route("/c2pa") {
                    // Configuration endpoint
                    get("/configuration") {
                        if (call.checkBearerAuth()) {
                            c2paConfigurationController.getConfiguration(call)
                        }
                    }
                    
                    // Signing endpoint
                    post("/sign") {
                        if (call.checkBearerAuth()) {
                            c2paSigningController.signManifest(call)
                        }
                    }
                }
            }
        }
    }
        .start(wait = true)
}
