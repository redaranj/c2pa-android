package org.contentauth.c2pa

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.*
import java.security.cert.Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.RSAKeyGenParameterSpec
import java.util.concurrent.Executor
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * StrongBox signer configuration
 * StrongBox is Android's hardware security module
 */
@RequiresApi(Build.VERSION_CODES.P)
data class StrongBoxSignerConfig(
    val keyTag: String,
    val accessControl: Int = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
)

/**
 * StrongBox Signing Extension
 */
@RequiresApi(Build.VERSION_CODES.P)
fun Signer(
    algorithm: SigningAlgorithm,
    certificateChainPEM: String,
    tsaURL: String? = null,
    strongBoxConfig: StrongBoxSignerConfig
): Signer {
    if (algorithm != SigningAlgorithm.ES256) {
        throw C2PAError.Api("StrongBox only supports ES256 (P-256)")
    }
    
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    
    // Check if key already exists
    var privateKey: PrivateKey? = null
    if (keyStore.containsAlias(strongBoxConfig.keyTag)) {
        privateKey = keyStore.getKey(strongBoxConfig.keyTag, null) as? PrivateKey
    }
    
    // Create key if it doesn't exist
    if (privateKey == null) {
        privateKey = createStrongBoxKey(strongBoxConfig)
    }
    
    return Signer.withCallback(algorithm, certificateChainPEM, tsaURL) { data ->
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        signature.sign()
    }
}

/**
 * Create a StrongBox key (internal helper)
 */
@RequiresApi(Build.VERSION_CODES.P)
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
        setUserAuthenticationRequired(true)
        setIsStrongBoxBacked(true)
    }
    
    keyPairGenerator.initialize(builder.build())
    val keyPair = keyPairGenerator.generateKeyPair()
    return keyPair.private
}

/**
 * Delete a StrongBox key
 */
fun deleteStrongBoxKey(keyTag: String): Boolean {
    return try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(keyTag)
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Additional Android-specific hardware security utilities
 * Hardware security helper functions
 */
object HardwareSecurity {
    
    /**
     * Check if StrongBox is available on this device
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun isStrongBoxAvailable(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
    }
    
    /**
     * Check if hardware-backed keystore is available
     */
    fun isHardwareBackedKeystoreAvailable(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if a key is hardware-backed
     */
    @RequiresApi(Build.VERSION_CODES.M)
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
     * Check if a key is StrongBox-backed
     */
    @RequiresApi(Build.VERSION_CODES.S)
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
    
    /**
     * Create a signer with biometric authentication
     */
    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun createBiometricSigner(
        activity: FragmentActivity,
        keyTag: String,
        algorithm: SigningAlgorithm,
        certificateChain: String,
        tsaURL: String? = null,
        promptTitle: String = "Authenticate to sign",
        promptSubtitle: String = "Use your biometric credential to sign the document",
        promptDescription: String? = null
    ): Signer = suspendCoroutine { continuation ->
        
        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    continuation.resumeWithException(
                        C2PAError.Api("Biometric authentication error: $errString")
                    )
                }
                
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try {
                        val signer = Signer.withKeystore(
                            algorithm,
                            certificateChain,
                            tsaURL,
                            keyTag
                        )
                        continuation.resume(signer)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
                
                override fun onAuthenticationFailed() {
                    continuation.resumeWithException(
                        C2PAError.Api("Biometric authentication failed")
                    )
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptTitle)
            .setSubtitle(promptSubtitle)
            .apply {
                promptDescription?.let { setDescription(it) }
            }
            .setNegativeButtonText("Cancel")
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
}