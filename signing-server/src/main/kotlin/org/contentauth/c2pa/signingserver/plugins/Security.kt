package org.contentauth.c2pa.signingserver.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import org.contentauth.c2pa.signingserver.config.ServerConfig
import org.contentauth.c2pa.signingserver.config.loadServerConfig

val ServerConfigKey = AttributeKey<ServerConfig>("ServerConfig")

fun Application.configureSecurity() {
    val config = loadServerConfig()
    attributes.put(ServerConfigKey, config)
    
    if (config.apiKeyRequired) {
        intercept(ApplicationCallPipeline.Plugins) {
            // Skip API key check for health endpoints
            if (call.request.path() == "/" || call.request.path() == "/health") {
                return@intercept
            }
            
            // Check for API endpoints
            if (call.request.path().startsWith("/api")) {
                val providedApiKey = call.request.header("X-API-Key")
                    ?: call.request.queryParameters["apiKey"]
                
                if (providedApiKey == null || providedApiKey != config.apiKey) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid or missing API key")
                    )
                    finish()
                }
            }
        }
    }
}