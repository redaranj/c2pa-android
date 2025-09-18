package org.contentauth.c2pa

import java.io.Closeable
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature

/**
 * Callback interface for custom signing operations
 */
interface SignCallback {
    fun sign(data: ByteArray): ByteArray
}

/**
 * Type aliases for web service signing
 */
typealias WebServiceSigner = (ByteArray) -> ByteArray

/**
 * C2PA Signer for signing manifests
 */
class Signer internal constructor(internal var ptr: Long) : Closeable {
    
    companion object {
        init {
            loadC2PALibraries()
        }
        
        /**
         * Create signer from certificates and private key
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromKeys(
            certsPEM: String,
            privateKeyPEM: String,
            algorithm: SigningAlgorithm,
            tsaURL: String? = null
        ): Signer {
            val info = SignerInfo(algorithm, certsPEM, privateKeyPEM, tsaURL)
            return fromInfo(info)
        }

        /**
         * Create signer from SignerInfo
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromInfo(info: SignerInfo): Signer {
            return executeC2PAOperation("Failed to create signer") {
                val handle = nativeFromInfo(
                    info.algorithm.description,
                    info.certificatePEM,
                    info.privateKeyPEM,
                    info.tsaURL
                )
                if (handle == 0L) null else Signer(handle)
            }
        }

        /**
         * Create signer with custom signing callback
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun withCallback(
            algorithm: SigningAlgorithm,
            certificateChainPEM: String,
            tsaURL: String? = null,
            sign: (ByteArray) -> ByteArray
        ): Signer {
            return executeC2PAOperation("Failed to create callback signer") {
                val callback = object : SignCallback {
                    override fun sign(data: ByteArray): ByteArray = sign(data)
                }
                val handle = nativeFromCallback(algorithm.description, certificateChainPEM, tsaURL, callback)
                if (handle == 0L) null else Signer(handle)
            }
        }

        @JvmStatic
        private external fun nativeFromInfo(
            algorithm: String,
            certificatePEM: String,
            privateKeyPEM: String,
            tsaURL: String?
        ): Long

        @JvmStatic
        private external fun nativeFromCallback(
            algorithm: String,
            certificateChain: String,
            tsaURL: String?,
            callback: SignCallback
        ): Long
        
        /**
         * Create signer for web service signing
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun withWebService(
            algorithm: SigningAlgorithm,
            certificateChainPEM: String,
            tsaURL: String? = null,
            signer: WebServiceSigner
        ): Signer {
            return withCallback(algorithm, certificateChainPEM, tsaURL, signer)
        }
        
        /**
         * Create signer for Android Keystore
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun withKeystore(
            algorithm: SigningAlgorithm,
            certificateChainPEM: String,
            tsaURL: String? = null,
            keystoreAlias: String
        ): Signer {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            val privateKey = keyStore.getKey(keystoreAlias, null) as? PrivateKey
                ?: throw C2PAError.Api("Key '$keystoreAlias' not found in keystore")
            
            val javaAlgorithm = when (algorithm) {
                SigningAlgorithm.ES256 -> "SHA256withECDSA"
                SigningAlgorithm.ES384 -> "SHA384withECDSA"
                SigningAlgorithm.ES512 -> "SHA512withECDSA"
                SigningAlgorithm.PS256 -> "SHA256withRSA/PSS"
                SigningAlgorithm.PS384 -> "SHA384withRSA/PSS"
                SigningAlgorithm.PS512 -> "SHA512withRSA/PSS"
                SigningAlgorithm.ED25519 -> throw C2PAError.Api("Ed25519 not supported by Android Keystore")
            }
            
            return withCallback(algorithm, certificateChainPEM, tsaURL) { data ->
                val signature = Signature.getInstance(javaAlgorithm)
                signature.initSign(privateKey)
                signature.update(data)
                signature.sign()
            }
        }
    }

    /**
     * Get the reserve size for this signer
     */
    @Throws(C2PAError::class)
    fun reserveSize(): Int {
        val size = reserveSizeNative(ptr)
        if (size < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to get reserve size")
        }
        if (size > Int.MAX_VALUE) {
            throw C2PAError.Api("Reserve size too large: $size")
        }
        return size.toInt()
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0L
        }
    }

    private external fun reserveSizeNative(handle: Long): Long
    private external fun free(handle: Long)
}