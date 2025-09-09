package org.contentauth.c2pa

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.StringWriter
import java.security.*
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import javax.security.auth.x500.X500Principal

/**
 * Certificate management for C2PA signing on Android
 */
object CertificateManager {
    
    init {
        // Ensure BouncyCastle provider is registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }
    
    data class CertificateConfig(
        val commonName: String,
        val organization: String,
        val organizationalUnit: String,
        val country: String,
        val state: String,
        val locality: String,
        val emailAddress: String? = null
    )
    
    /**
     * Creates a Certificate Signing Request (CSR) for a hardware-backed key
     * 
     * This is the Android equivalent of iOS's Secure Enclave CSR generation.
     * Android's advantage: We can use standard BouncyCastle APIs since Android
     * allows signing operations with hardware-backed keys through the KeyStore API.
     */
    @JvmStatic
    fun createCSR(
        keyAlias: String,
        config: CertificateConfig
    ): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        // Get the private key reference (not the actual key material for hardware-backed keys)
        val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey
            ?: throw C2PAError.Api("Private key not found for alias: $keyAlias")
        
        // Get the public key
        val certificate = keyStore.getCertificate(keyAlias)
            ?: throw C2PAError.Api("Certificate not found for alias: $keyAlias")
        val publicKey = certificate.publicKey
        
        // Build X500 subject
        val subjectDN = buildX500Name(config)
        
        // Create CSR using BouncyCastle
        // Unlike iOS, Android allows us to use the hardware-backed private key for signing
        // through the KeyStore API, so we don't need to manually construct the CSR
        val csrBuilder = JcaPKCS10CertificationRequestBuilder(subjectDN, publicKey)
        
        // Create content signer using the hardware-backed private key
        val contentSigner = createContentSigner(privateKey)
        
        // Build the CSR
        val csr = csrBuilder.build(contentSigner)
        
        // Convert to PEM format
        return csrToPEM(csr)
    }
    
    /**
     * Creates a CSR for a StrongBox-backed key (Android P+)
     * StrongBox is Android's hardware security module, similar to iOS's Secure Enclave
     */
    @JvmStatic
    fun createStrongBoxCSR(
        config: StrongBoxSignerConfig,
        certificateConfig: CertificateConfig
    ): String {
        // Ensure StrongBox key exists or create it
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        if (!keyStore.containsAlias(config.keyTag)) {
            // Create StrongBox key if it doesn't exist
            createStrongBoxKey(config)
        }
        
        return createCSR(config.keyTag, certificateConfig)
    }
    
    /**
     * Generate a new hardware-backed key specifically for CSR generation
     */
    @JvmStatic
    fun generateHardwareKey(
        keyAlias: String,
        requireStrongBox: Boolean = false
    ): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setDigests(KeyProperties.DIGEST_SHA256)
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            // Generate a self-signed certificate for the key
            setCertificateSubject(X500Principal("CN=Temporary, O=C2PA, C=US"))
            setCertificateSerialNumber(java.math.BigInteger.valueOf(System.currentTimeMillis()))
            setCertificateNotBefore(java.util.Date(System.currentTimeMillis() - 86400000L))
            setCertificateNotAfter(java.util.Date(System.currentTimeMillis() + 86400000L * 365))
            
            if (requireStrongBox) {
                setIsStrongBoxBacked(true)
            }
            
            // For CSR generation, we don't require user authentication
            setUserAuthenticationRequired(false)
        }
        
        keyPairGenerator.initialize(builder.build())
        return keyPairGenerator.generateKeyPair()
    }
    
    /**
     * Check if a key is hardware-backed
     */
    @JvmStatic
    fun isKeyHardwareBacked(keyAlias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey ?: return false
            val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
            val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)
            
            keyInfo.isInsideSecureHardware
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if a key is StrongBox-backed (Android P+)
     */
    @RequiresApi(Build.VERSION_CODES.S)
    @JvmStatic
    fun isKeyStrongBoxBacked(keyAlias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey ?: return false
            val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
            val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)
            
            keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } catch (e: Exception) {
            false
        }
    }
    
    // Private helper methods
    
    private fun buildX500Name(config: CertificateConfig): X500Name {
        val builder = StringBuilder()
        builder.append("CN=").append(config.commonName)
        builder.append(", O=").append(config.organization)
        builder.append(", OU=").append(config.organizationalUnit)
        builder.append(", L=").append(config.locality)
        builder.append(", ST=").append(config.state)
        builder.append(", C=").append(config.country)
        config.emailAddress?.let {
            builder.append(", EMAILADDRESS=").append(it)
        }
        return X500Name(builder.toString())
    }
    
    private fun createContentSigner(privateKey: PrivateKey): ContentSigner {
        // For EC keys, use SHA256withECDSA
        val signatureAlgorithm = when (privateKey.algorithm) {
            "EC" -> "SHA256withECDSA"
            "RSA" -> "SHA256withRSA"
            else -> throw C2PAError.Api("Unsupported key algorithm: ${privateKey.algorithm}")
        }
        
        // Create a custom ContentSigner that uses Android KeyStore for signing
        return AndroidKeyStoreContentSigner(privateKey, signatureAlgorithm)
    }
    
    private fun csrToPEM(csr: PKCS10CertificationRequest): String {
        val writer = StringWriter()
        val pemWriter = PemWriter(writer)
        val pemObject = PemObject("CERTIFICATE REQUEST", csr.encoded)
        pemWriter.writeObject(pemObject)
        pemWriter.close()
        return writer.toString()
    }
    
    private fun createStrongBoxKey(config: StrongBoxSignerConfig): PrivateKey {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        
        val builder = KeyGenParameterSpec.Builder(
            config.keyTag,
            config.accessControl
        ).apply {
            setDigests(KeyProperties.DIGEST_SHA256)
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            setCertificateSubject(X500Principal("CN=StrongBox Key, O=C2PA, C=US"))
            setCertificateSerialNumber(java.math.BigInteger.valueOf(System.currentTimeMillis()))
            setCertificateNotBefore(java.util.Date(System.currentTimeMillis() - 86400000L))
            setCertificateNotAfter(java.util.Date(System.currentTimeMillis() + 86400000L * 365))
            setUserAuthenticationRequired(false) // For CSR generation
            setIsStrongBoxBacked(true)
        }
        
        keyPairGenerator.initialize(builder.build())
        val keyPair = keyPairGenerator.generateKeyPair()
        return keyPair.private
    }
    
    /**
     * Custom ContentSigner that uses Android KeyStore for signing operations
     */
    private class AndroidKeyStoreContentSigner(
        private val privateKey: PrivateKey,
        private val signatureAlgorithm: String
    ) : ContentSigner {
        
        private val signature = Signature.getInstance(signatureAlgorithm)
        private val outputStream = java.io.ByteArrayOutputStream()
        
        init {
            signature.initSign(privateKey)
        }
        
        override fun getAlgorithmIdentifier(): AlgorithmIdentifier {
            return when (signatureAlgorithm) {
                "SHA256withECDSA" -> AlgorithmIdentifier(
                    ASN1ObjectIdentifier("1.2.840.10045.4.3.2") // ecdsaWithSHA256
                )
                "SHA256withRSA" -> AlgorithmIdentifier(
                    PKCSObjectIdentifiers.sha256WithRSAEncryption
                )
                else -> throw IllegalArgumentException("Unsupported algorithm: $signatureAlgorithm")
            }
        }
        
        override fun getOutputStream(): java.io.OutputStream = outputStream
        
        override fun getSignature(): ByteArray {
            val dataToSign = outputStream.toByteArray()
            signature.update(dataToSign)
            return signature.sign()
        }
    }
}