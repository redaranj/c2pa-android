package org.contentauth.c2pa.signingserver.models

import kotlinx.serialization.Serializable

@Serializable
data class C2PASigningRequest(val claim: String) // Base64-encoded bytes to be signed

@Serializable
data class C2PASigningResponse(val signature: String) // Base64 encoded signature

@Serializable
data class C2PAConfiguration(
    val algorithm: String,
    val timestamp_url: String,
    val signing_url: String,
    val certificate_chain: String,
)
