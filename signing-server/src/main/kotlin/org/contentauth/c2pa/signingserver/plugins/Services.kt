package org.contentauth.c2pa.signingserver.plugins

import io.ktor.server.application.*
import io.ktor.util.*
import org.contentauth.c2pa.signingserver.services.CertificateSigningService
import org.contentauth.c2pa.signingserver.services.C2PASigningService

// Service storage using Ktor's Attributes
val CertificateServiceKey = AttributeKey<CertificateSigningService>("CertificateService")
val C2PAServiceKey = AttributeKey<C2PASigningService>("C2PAService")

fun Application.configureServices() {
    // Initialize services
    val certificateService = CertificateSigningService()
    val c2paService = C2PASigningService(certificateService)
    
    // Store services in application attributes
    attributes.put(CertificateServiceKey, certificateService)
    attributes.put(C2PAServiceKey, c2paService)
}