package org.contentauth.c2paexample

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.subtle.EllipticCurves
import com.google.crypto.tink.subtle.Validators
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.util.Base64

/**
 * Helper class for signing with Tink using existing PEM keys
 */
object TinkSignatureHelper {
    
    init {
        // Register Tink signature primitives
        TinkConfig.register()
        SignatureConfig.register()
    }
    
    /**
     * Sign data using an existing EC private key in PEM format
     * This bridges between PEM keys and Tink's signing primitives
     */
    fun signWithPEMKey(data: ByteArray, pemPrivateKey: String): ByteArray {
        // Since Tink doesn't directly support PEM import, we'll use its low-level primitives
        // For ES256 (P-256 with SHA256), we can use Tink's EcdsaSignJce
        
        val privateKey = parseECPrivateKeyFromPEM(pemPrivateKey)
        
        // Use Tink's ECDSA signing implementation
        val signer = com.google.crypto.tink.subtle.EcdsaSignJce(
            privateKey,
            EllipticCurves.EcdsaEncoding.DER,
            "SHA256"
        )
        
        return signer.sign(data)
    }
    
    /**
     * Parse EC private key from PEM format
     * Handles both SEC1 and PKCS8 formats
     */
    private fun parseECPrivateKeyFromPEM(pemString: String): ECPrivateKey {
        val privateKeyStr = pemString
            .replace("-----BEGIN EC PRIVATE KEY-----", "")
            .replace("-----END EC PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        
        val keyBytes = Base64.getDecoder().decode(privateKeyStr)
        
        // For SEC1 format (common for EC keys), we need to extract the private value
        // SEC1 format: SEQUENCE { version, privateKey, [0] parameters, [1] publicKey }
        return if (keyBytes[0] == 0x30.toByte() && keyBytes[2] == 0x02.toByte()) {
            // This looks like SEC1 format
            parseSEC1ECPrivateKey(keyBytes)
        } else {
            // Try PKCS8
            val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePrivate(keySpec) as ECPrivateKey
        }
    }
    
    /**
     * Parse SEC1 formatted EC private key
     */
    private fun parseSEC1ECPrivateKey(sec1Bytes: ByteArray): ECPrivateKey {
        // SEC1 format parsing - simplified for P-256
        // Skip the SEQUENCE tag and length
        var offset = 2
        
        // Skip version (should be 1)
        offset += 3 // Tag (02), length (01), value (01)
        
        // Get private key value
        val privateKeyLength = sec1Bytes[offset + 1].toInt()
        offset += 2 // Skip OCTET STRING tag and length
        
        val privateKeyBytes = sec1Bytes.sliceArray(offset until offset + privateKeyLength)
        val privateKeyValue = BigInteger(1, privateKeyBytes)
        
        // For P-256 (secp256r1)
        val ecSpec = EllipticCurves.getCurveSpec(EllipticCurves.CurveType.NIST_P256)
        val keySpec = ECPrivateKeySpec(privateKeyValue, ecSpec)
        
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(keySpec) as ECPrivateKey
    }
}