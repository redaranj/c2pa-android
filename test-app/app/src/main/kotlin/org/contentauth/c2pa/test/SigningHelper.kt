package org.contentauth.c2pa.test

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Simple signing helper for Android C2PA callback signers
 * (Copied from library's InstrumentedTests.kt)
 */
object SigningHelper {
    
    /**
     * Sign data using an existing private key in PEM format
     */
    fun signWithPEMKey(data: ByteArray, pemPrivateKey: String, algorithm: String = "ES256"): ByteArray {
        val privateKeyStr = pemPrivateKey
            .replace("-----BEGIN EC PRIVATE KEY-----", "")
            .replace("-----END EC PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        
        val keyBytes = Base64.getDecoder().decode(privateKeyStr)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(keySpec)
        val (hashAlgorithm, componentSize) = when (algorithm.uppercase()) {
            "ES256" -> Pair("SHA256withECDSA", 32)
            "ES384" -> Pair("SHA384withECDSA", 48)
            "ES512" -> Pair("SHA512withECDSA", 66) // P-521 uses 66 bytes
            else -> throw IllegalArgumentException("Unsupported algorithm: $algorithm")
        }
        
        val signature = Signature.getInstance(hashAlgorithm)
        signature.initSign(privateKey)
        signature.update(data)
        val derSignature = signature.sign()
        
        return convertDERToRaw(derSignature, componentSize)
    }
    
    /**
     * Convert DER-encoded ECDSA signature
     */
    private fun convertDERToRaw(derSignature: ByteArray, componentSize: Int): ByteArray {
        // DER format: 30 [total-len] 02 [r-len] [r] 02 [s-len] [s]
        if (derSignature[0] != 0x30.toByte()) {
            return derSignature // Not DER, return as-is
        }
        
        var offset = 2 // Skip SEQUENCE tag and length
        
        // Parse R
        if (derSignature[offset] != 0x02.toByte()) {
            return derSignature // Not INTEGER, return as-is
        }
        offset++
        val rLength = derSignature[offset].toInt() and 0xFF
        offset++
        val r = derSignature.sliceArray(offset until offset + rLength)
        offset += rLength
        
        // Parse S
        if (offset >= derSignature.size || derSignature[offset] != 0x02.toByte()) {
            return derSignature // Not INTEGER, return as-is
        }
        offset++
        val sLength = derSignature[offset].toInt() and 0xFF
        offset++
        val s = derSignature.sliceArray(offset until offset + sLength)
        
        // Format components to exact size
        val rFormatted = formatComponent(r, componentSize)
        val sFormatted = formatComponent(s, componentSize)
        
        // Return concatenated R + S
        return rFormatted + sFormatted
    }
    
    /**
     * Format a signature component to exact size by removing leading zeros
     * and padding if necessary
     */
    private fun formatComponent(bytes: ByteArray, targetSize: Int): ByteArray {
        // Remove leading zeros
        var start = 0
        while (start < bytes.size && bytes[start] == 0.toByte()) {
            start++
        }
        val trimmed = if (start == 0) bytes else bytes.sliceArray(start until bytes.size)
        
        return when {
            trimmed.size == targetSize -> trimmed
            trimmed.size < targetSize -> ByteArray(targetSize - trimmed.size) + trimmed
            else -> trimmed.takeLast(targetSize).toByteArray()
        }
    }
}