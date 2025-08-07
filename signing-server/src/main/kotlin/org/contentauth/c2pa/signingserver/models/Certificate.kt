package org.contentauth.c2pa.signingserver.models

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

@Serializable
data class CertificateSigningRequest(
    val csr: String,  // PEM-encoded CSR
    val metadata: CSRMetadata? = null
)

@Serializable
data class CSRMetadata(
    val deviceId: String? = null,
    val appVersion: String? = null,
    val purpose: String? = null
)

@Serializable
data class SignedCertificateResponse(
    val certificateId: String,
    val certificateChain: String,  // PEM-encoded certificate chain
    val expiresAt: Instant,
    val serialNumber: String
)