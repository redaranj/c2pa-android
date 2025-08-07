package org.contentauth.c2pa.signingserver.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"]
            "[$httpMethod] ${call.request.path()} - $status - $userAgent"
        }
    }
    
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError, 
                mapOf("error" to (cause.message ?: "Internal server error"))
            )
        }
        
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                mapOf("error" to "Not found")
            )
        }
    }
}