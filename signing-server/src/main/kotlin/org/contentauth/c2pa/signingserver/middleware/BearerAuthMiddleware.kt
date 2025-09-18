package org.contentauth.c2pa.signingserver.middleware

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.request.header
import io.ktor.server.response.respond

/**
 * Bearer token authentication check
 */
suspend fun ApplicationCall.checkBearerAuth(): Boolean {
    val authHeader = request.header(HttpHeaders.Authorization)
    
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        application.log.warn("Missing or invalid Authorization header")
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing or invalid Authorization header"))
        return false
    }
    
    val token = authHeader.removePrefix("Bearer ").trim()
    val expectedToken = System.getenv("BEARER_TOKEN")
    
    if (expectedToken.isNullOrEmpty()) {
        application.log.error("BEARER_TOKEN environment variable not set")
        respond(HttpStatusCode.InternalServerError, mapOf("error" to "Server authentication not configured"))
        return false
    }
    
    if (token != expectedToken) {
        application.log.warn("Invalid bearer token")
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid bearer token"))
        return false
    }
    
    // Token is valid
    return true
}