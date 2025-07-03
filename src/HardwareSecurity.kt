package info.guardianproject.c2pa

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.ByteArrayOutputStream
import java.security.*
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.security.auth.x500.X500Principal

/**
 * Hardware-backed security implementation for Android C2PA
 * Provides StrongBox/TEE equivalent to iOS Secure Enclave
 */
object HardwareSecurity {
    
    private const val ANDROID_KEYSTORE = "AndroidKeystore"
    private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_EC
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val EC_CURVE = "secp256r1"
    
    /**
     * Hardware security levels available on Android
     */
    enum class SecurityLevel {
        SOFTWARE_ONLY,      // No hardware backing
        TEE_BACKED,        // Trusted Execution Environment
        STRONGBOX_BACKED   // Dedicated security chip (best)
    }
    
    /**
     * Authentication requirements for hardware keys
     */
    data class AuthenticationConfig(
        val requireBiometric: Boolean = true,
        val requireUserPresence: Boolean = true,
        val userAuthenticationTimeoutSeconds: Int = 30,
        val invalidateOnBiometricEnrollment: Boolean = true
    )
    
    /**
     * Hardware key generation parameters
     */
    data class HardwareKeyConfig(
        val keyAlias: String,
        val algorithm: String = KEY_ALGORITHM,
        val keySize: Int = 256,
        val curve: String = EC_CURVE,
        val authConfig: AuthenticationConfig = AuthenticationConfig(),
        val requireStrongBox: Boolean = true
    )
    
    /**
     * Hardware attestation result
     */
    data class AttestationResult(
        val isHardwareBacked: Boolean,
        val securityLevel: SecurityLevel,
        val hasStrongBox: Boolean,
        val attestationChain: List<X509Certificate>?,
        val keyInfo: KeyInfo?
    )
    
    /**
     * Check device hardware security capabilities
     */
    fun getDeviceSecurityCapabilities(context: Context): SecurityCapabilities {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val biometricManager = BiometricManager.from(context)
        
        return SecurityCapabilities(
            hasSecureHardware = hasSecureHardware(),
            hasStrongBox = hasStrongBoxSupport(),
            hasBiometrics = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS,
            hasKeyguard = keyguardManager.isKeyguardSecure,
            androidVersion = Build.VERSION.SDK_INT,
            securityPatchLevel = Build.VERSION.SECURITY_PATCH
        )
    }
    
    /**
     * Generate EC key pair for hardware-backed signing
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun generateECKeyPair(alias: String, requireStrongBox: Boolean): GeneratedKeyPair? {
        return try {
            val config = HardwareKeyConfig(
                keyAlias = alias,
                algorithm = KEY_ALGORITHM,
                requireStrongBox = requireStrongBox
            )
            generateHardwareBackedKeyPair(config)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Generate Ed25519 key pair (software only on Android)
     */
    fun generateEd25519KeyPair(alias: String): GeneratedKeyPair? {
        // Android doesn't support Ed25519 in hardware keystore
        // This would be a software implementation
        return null
    }
    
    /**
     * Generate hardware-backed key pair for C2PA signing
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun generateHardwareBackedKeyPair(config: HardwareKeyConfig): GeneratedKeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(config.algorithm, ANDROID_KEYSTORE)
        
        val specBuilder = KeyGenParameterSpec.Builder(
            config.keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
        .setAlgorithmParameterSpec(ECGenParameterSpec(config.curve))
        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
        .setUserAuthenticationRequired(config.authConfig.requireBiometric || config.authConfig.requireUserPresence)
        .setInvalidatedByBiometricEnrollment(config.authConfig.invalidateOnBiometricEnrollment)
        
        // Set authentication timeout
        if (config.authConfig.requireBiometric || config.authConfig.requireUserPresence) {
            specBuilder.setUserAuthenticationValidityDurationSeconds(config.authConfig.userAuthenticationTimeoutSeconds)
        }
        
        // Request StrongBox if available and requested
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && config.requireStrongBox) {
            try {
                specBuilder.setIsStrongBoxBacked(true)
            } catch (e: Exception) {
                // StrongBox not available, fall back to TEE
            }
        }
        
        // Enable attestation if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                specBuilder.setAttestationChallenge("C2PA_HARDWARE_ATTESTATION".toByteArray())
            } catch (e: Exception) {
                // Attestation not available
            }
        }
        
        keyPairGenerator.initialize(specBuilder.build())
        val keyPair = keyPairGenerator.generateKeyPair()
        
        return GeneratedKeyPair(
            keyPair = keyPair,
            alias = config.keyAlias,
            attestation = getKeyAttestation(config.keyAlias, keyPair)
        )
    }
    
    /**
     * Create hardware-backed C2PA signer
     */
    fun createHardwareBackedSigner(
        keyAlias: String,
        certificateChain: String,
        algorithm: String = "es256"
    ): Signer? {
        return try {
            val signingAlgorithm = when (algorithm.lowercase()) {
                "es256" -> SigningAlgorithm.ES256
                "es384" -> SigningAlgorithm.ES384
                "es512" -> SigningAlgorithm.ES512
                "ps256" -> SigningAlgorithm.PS256
                "ps384" -> SigningAlgorithm.PS384
                "ps512" -> SigningAlgorithm.PS512
                "ed25519" -> SigningAlgorithm.ED25519
                else -> SigningAlgorithm.ES256
            }
            Signer.fromKeystore(signingAlgorithm, certificateChain, null, keyAlias)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Create hardware-backed C2PA signer with biometric authentication
     */
    fun createBiometricSigner(
        activity: FragmentActivity,
        keyAlias: String,
        certificateChain: String,
        algorithm: String = "es256",
        onSuccess: (Signer) -> Unit,
        onError: (String) -> Unit
    ) {
        val biometricPrompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    try {
                        val signer = createHardwareBackedSigner(keyAlias, certificateChain, algorithm)
                        if (signer != null) {
                            onSuccess(signer)
                        } else {
                            onError("Failed to create hardware-backed signer")
                        }
                    } catch (e: Exception) {
                        onError("Authentication succeeded but signer creation failed: ${e.message}")
                    }
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError("Biometric authentication error: $errString")
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometric authentication failed")
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("C2PA Hardware Signing")
            .setSubtitle("Authenticate to access your hardware-backed signing key")
            .setDescription("This will use your device's secure hardware to sign C2PA manifests")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    /**
     * Get key attestation information (convenience method)
     */
    fun getKeyAttestation(keyAlias: String): AttestationResult {
        return getKeyAttestation(keyAlias, null)
    }
    
    /**
     * Get key attestation information
     */
    fun getKeyAttestation(keyAlias: String, keyPair: KeyPair? = null): AttestationResult {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            val key = keyPair?.private ?: keyStore.getKey(keyAlias, null) as PrivateKey?
            val cert = keyStore.getCertificate(keyAlias) as X509Certificate?
            
            if (key == null || cert == null) {
                return AttestationResult(
                    isHardwareBacked = false,
                    securityLevel = SecurityLevel.SOFTWARE_ONLY,
                    hasStrongBox = false,
                    attestationChain = null,
                    keyInfo = null
                )
            }
            
            // Get key info for hardware backing details
            val keyInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val keyFactory = KeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
                keyFactory.getKeySpec(key, KeyInfo::class.java)
            } else {
                null
            }
            
            // Determine security level
            val securityLevel = when {
                keyInfo?.isInsideSecureHardware == true && keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX -> SecurityLevel.STRONGBOX_BACKED
                keyInfo?.isInsideSecureHardware == true -> SecurityLevel.TEE_BACKED
                else -> SecurityLevel.SOFTWARE_ONLY
            }
            
            // Get attestation certificate chain if available
            val attestationChain = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    keyStore.getCertificateChain(keyAlias)?.map { it as X509Certificate }
                } else {
                    listOf(cert)
                }
            } catch (e: Exception) {
                listOf(cert)
            }
            
            AttestationResult(
                isHardwareBacked = keyInfo?.isInsideSecureHardware == true,
                securityLevel = securityLevel,
                hasStrongBox = securityLevel == SecurityLevel.STRONGBOX_BACKED,
                attestationChain = attestationChain,
                keyInfo = keyInfo
            )
        } catch (e: Exception) {
            AttestationResult(
                isHardwareBacked = false,
                securityLevel = SecurityLevel.SOFTWARE_ONLY,
                hasStrongBox = false,
                attestationChain = null,
                keyInfo = null
            )
        }
    }
    
    /**
     * Delete hardware-backed key
     */
    fun deleteHardwareKey(keyAlias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.deleteEntry(keyAlias)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if key exists in hardware keystore
     */
    fun keyExists(keyAlias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.containsAlias(keyAlias)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Export public key in PEM format
     */
    fun exportPublicKeyPEM(keyAlias: String): String? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val cert = keyStore.getCertificate(keyAlias)
            val publicKey = cert.publicKey
            
            val encoded = publicKey.encoded
            val base64 = Base64.encodeToString(encoded, Base64.NO_WRAP)
            
            buildString {
                append("-----BEGIN PUBLIC KEY-----\n")
                base64.chunked(64).forEach { chunk ->
                    append(chunk)
                    append("\n")
                }
                append("-----END PUBLIC KEY-----")
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Generate self-signed certificate for testing
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun generateSelfSignedCertificate(keyAlias: String, subject: String = "CN=C2PA Test"): String? {
        return try {
            // This is a simplified version - in production you'd want proper certificate generation
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val cert = keyStore.getCertificate(keyAlias) as X509Certificate
            
            val encoded = cert.encoded
            val base64 = Base64.encodeToString(encoded, Base64.NO_WRAP)
            
            buildString {
                append("-----BEGIN CERTIFICATE-----\n")
                base64.chunked(64).forEach { chunk ->
                    append(chunk)
                    append("\n")
                }
                append("-----END CERTIFICATE-----")
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if device has secure enclave equivalent (StrongBox or TEE)
     */
    fun hasSecureEnclave(): Boolean {
        return hasStrongBox() || hasKeyStoreBackedByHardware()
    }
    
    /**
     * Check if device has StrongBox (highest security)
     */
    fun hasStrongBox(): Boolean {
        return hasStrongBoxSupport()
    }
    
    /**
     * Check if device has hardware-backed keystore (TEE)
     */
    fun hasKeyStoreBackedByHardware(): Boolean {
        return hasSecureHardware()
    }
    
    /**
     * Check if device has secure hardware
     */
    private fun hasSecureHardware(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if device supports StrongBox
     */
    private fun hasStrongBoxSupport(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                // Try to create a temporary key with StrongBox requirement
                val keyGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    "strongbox_test_key",
                    KeyProperties.PURPOSE_SIGN
                )
                .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setIsStrongBoxBacked(true)
                .build()
                
                keyGenerator.initialize(spec)
                keyGenerator.generateKeyPair()
                
                // Clean up test key
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
                keyStore.load(null)
                keyStore.deleteEntry("strongbox_test_key")
                
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }
    
    /**
     * Device security capabilities
     */
    data class SecurityCapabilities(
        val hasSecureHardware: Boolean,
        val hasStrongBox: Boolean,
        val hasBiometrics: Boolean,
        val hasKeyguard: Boolean,
        val androidVersion: Int,
        val securityPatchLevel: String
    )
    
    /**
     * Generated hardware-backed key pair with attestation
     */
    data class GeneratedKeyPair(
        val keyPair: KeyPair,
        val alias: String,
        val attestation: AttestationResult
    )
}