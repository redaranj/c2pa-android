package org.contentauth.c2pa.signingserver

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.contentauth.c2pa.signingserver.controllers.C2PASigningController
import org.contentauth.c2pa.signingserver.controllers.CertificateSigningController
import org.contentauth.c2pa.signingserver.services.C2PASigningService
import org.contentauth.c2pa.signingserver.services.CertificateSigningService

fun main() {
    val certificateSigningService = CertificateSigningService()
    val c2paSigningService = C2PASigningService(certificateSigningService)

    val c2paSigningController = C2PASigningController(c2paSigningService)
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
            // C2PA signing endpoint
            post("/api/v1/c2pa/sign") { c2paSigningController.signManifest(call) }

            // Certificate signing endpoint
            post("/api/v1/certificates/sign") { certificateSigningController.signCSR(call) }

            // Health check
            get("/health") { call.respond(mapOf("status" to "ok", "version" to "1.0.0")) }
        }
    }
        .start(wait = true)
}
