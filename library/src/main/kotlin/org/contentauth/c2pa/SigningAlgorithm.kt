package org.contentauth.c2pa

/**
 * Signing algorithms
 */
enum class SigningAlgorithm {
    ES256,
    ES384,
    ES512,
    PS256,
    PS384,
    PS512,
    ED25519,
    ;

    val cValue: Int
        get() = ordinal

    val description: String
        get() = name.lowercase()
}
