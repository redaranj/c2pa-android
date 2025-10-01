package org.contentauth.c2pa

/**
 * Signer information
 */
data class SignerInfo(
    val algorithm: SigningAlgorithm,
    val certificatePEM: String,
    val privateKeyPEM: String,
    val tsaURL: String? = null
)