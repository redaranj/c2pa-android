package org.contentauth.c2pa.signingserver.models

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

@Serializable
data class C2PASigningRequest(
    val manifestJSON: String,
    val format: String  // e.g., "image/jpeg"
)

@Serializable
data class C2PASigningResponse(
    val manifestStore: ByteArray,  // Binary manifest store data
    val signatureInfo: SignatureInfo
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as C2PASigningResponse

        if (!manifestStore.contentEquals(other.manifestStore)) return false
        if (signatureInfo != other.signatureInfo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = manifestStore.contentHashCode()
        result = 31 * result + signatureInfo.hashCode()
        return result
    }
}

@Serializable
data class SignatureInfo(
    val algorithm: String,
    val certificateChain: String? = null,
    val timestamp: Instant
)