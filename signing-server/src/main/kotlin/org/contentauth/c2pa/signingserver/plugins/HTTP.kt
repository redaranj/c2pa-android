package org.contentauth.c2pa.signingserver.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*

fun Application.configureHTTP() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Origin)
        allowHeader("X-Requested-With")
        allowHeader(HttpHeaders.UserAgent)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        
        anyHost() // Configure properly for production
    }
    
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
        header("X-C2PA-Server", "1.0.0")
    }
}