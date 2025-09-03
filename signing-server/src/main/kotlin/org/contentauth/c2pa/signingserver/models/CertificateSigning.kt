package org.contentauth.c2pa.signingserver.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class CertificateSigningRequest(
    val csr: String,  // PEM-encoded CSR
    val metadata: CSRSigningMetadata? = null
)

@Serializable
data class CSRSigningMetadata(
    val deviceId: String? = null,
    val appVersion: String? = null,
    val purpose: String? = null
)

@Serializable
data class SignedCertificateSigningResponse(
    val certificateId: String,
    val certificateChain: String,  // PEM-encoded certificate chain
    @Contextual val expiresAt: Instant,
    val serialNumber: String
)
