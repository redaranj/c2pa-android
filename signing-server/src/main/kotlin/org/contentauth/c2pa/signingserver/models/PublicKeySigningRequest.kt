package org.contentauth.c2pa.signingserver.models

import kotlinx.serialization.Serializable

@Serializable
data class PublicKeySigningRequest(
        val publicKey: String, // PEM-encoded public key
        val subject: SubjectInfo? = null,
        val metadata: CSRSigningMetadata? = null
)

@Serializable
data class SubjectInfo(
        val commonName: String = "C2PA Hardware Key",
        val organization: String = "C2PA Example App",
        val organizationalUnit: String = "Mobile",
        val country: String = "US",
        val state: String = "CA",
        val locality: String = "San Francisco"
)
