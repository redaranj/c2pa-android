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
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.Executor
import javax.security.auth.x500.X500Principal
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * StrongBox signer configuration StrongBox is Android's hardware security module (equivalent to iOS
 * Secure Enclave)
 */
data class StrongBoxSignerConfig(
        val keyTag: String,
        val accessControl: Int = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        val requireUserAuthentication: Boolean = false
)

/** StrongBox Signing Extension - mirrors iOS Secure Enclave implementation */
fun Signer.Companion.withStrongBox(
        algorithm: SigningAlgorithm,
        certificateChainPEM: String,
        tsaURL: String? = null,
        strongBoxConfig: StrongBoxSignerConfig
): Signer {
    require(algorithm == SigningAlgorithm.ES256) { "StrongBox only supports ES256 (P-256)" }

    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    // Get or create StrongBox key
    val privateKey =
            keyStore.getKey(strongBoxConfig.keyTag, null) as? PrivateKey
                    ?: createStrongBoxKey(strongBoxConfig)

    return withCallback(algorithm, certificateChainPEM, tsaURL) { data ->
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }
    }
}

/** Create a StrongBox key - mirrors iOS createSecureEnclaveKey */
private fun createStrongBoxKey(config: StrongBoxSignerConfig): PrivateKey =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
            initialize(
                    KeyGenParameterSpec.Builder(config.keyTag, config.accessControl)
                            .apply {
                                setDigests(KeyProperties.DIGEST_SHA256)
                                setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                                setUserAuthenticationRequired(config.requireUserAuthentication)
                                setIsStrongBoxBacked(true)
                                // Self-signed cert for the key
                                setCertificateSubject(X500Principal("CN=StrongBox Key"))
                                setCertificateSerialNumber(java.math.BigInteger.valueOf(1))
                                setCertificateNotBefore(Date())
                                setCertificateNotAfter(
                                        Date(
                                                System.currentTimeMillis() +
                                                        365L * 24 * 60 * 60 * 1000
                                        )
                                )
                            }
                            .build()
            )
            generateKeyPair().private
        }

/** Delete a StrongBox key - mirrors iOS deleteSecureEnclaveKey */
fun deleteStrongBoxKey(keyTag: String): Boolean =
        try {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(keyTag)
            }
            true
        } catch (e: Exception) {
            false
        }

/** Hardware security utilities - mirrors iOS's Signer extensions */
object HardwareSecurity {

    /**
     * Creates a CSR for a hardware-backed key and submits it to the signing server Returns a Signer
     * configured with the signed certificate
     */
    suspend fun createSignerWithCSR(
            keyAlias: String,
            certificateConfig: CertificateManager.CertificateConfig,
            signingServerUrl: String = SigningServerClient.LOCAL_SERVER,
            apiKey: String? = null,
            requireStrongBox: Boolean = false
    ): Signer {
        // Generate hardware-backed key if it doesn't exist
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(keyAlias)) {
            CertificateManager.generateHardwareKey(keyAlias, requireStrongBox)
        }

        // Generate CSR
        val csr = CertificateManager.createCSR(keyAlias, certificateConfig)

        // Submit to signing server
        val client = SigningServerClient(signingServerUrl, apiKey)
        val metadata =
                SigningServerClient.CSRMetadata(
                        deviceId = Build.ID,
                        appVersion = "1.0.0", // Version can be passed as parameter if needed
                        purpose = "c2pa-signing"
                )

        val response = client.signCSR(csr, metadata).getOrThrow()

        // Create signer with the certificate chain
        return Signer.withCallback(
                SigningAlgorithm.ES256,
                response.certificateChain,
                null // TSA URL if needed
        ) { dataToSign ->
            // Sign with hardware key
            val privateKey = keyStore.getKey(keyAlias, null) as PrivateKey
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(dataToSign)
            signature.sign()
        }
    }

    /** Creates a CSR for a StrongBox key and submits it to the signing server */
    suspend fun createStrongBoxSignerWithCSR(
            algorithm: SigningAlgorithm,
            strongBoxConfig: StrongBoxSignerConfig,
            certificateConfig: CertificateManager.CertificateConfig,
            tsaURL: String? = null,
            signingServerUrl: String = SigningServerClient.LOCAL_SERVER,
            apiKey: String? = null
    ): Signer {
        // Generate CSR for StrongBox key
        val csr = CertificateManager.createStrongBoxCSR(strongBoxConfig, certificateConfig)

        // Submit to signing server
        val client = SigningServerClient(signingServerUrl, apiKey)
        val metadata =
                SigningServerClient.CSRMetadata(
                        deviceId = Build.ID,
                        appVersion = "1.0.0", // Version can be passed as parameter if needed
                        purpose = "strongbox-signing"
                )

        val response = client.signCSR(csr, metadata).getOrThrow()

        // Create signer with the certificate chain and StrongBox key using callback
        return Signer.withCallback(
                algorithm = algorithm,
                certificateChainPEM = response.certificateChain,
                tsaURL = tsaURL
        ) { data ->
            // Sign with StrongBox key
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val privateKey = keyStore.getKey(strongBoxConfig.keyTag, null) as PrivateKey

            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(data)
            signature.sign()
        }
    }

    /** Check if StrongBox is available (equivalent to iOS Secure Enclave check) */
    fun isStrongBoxAvailable(context: Context): Boolean =
            context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")

    /** Check if hardware-backed keystore is available */
    fun isHardwareBackedKeystoreAvailable(): Boolean =
            try {
                KeyStore.getInstance("AndroidKeyStore").load(null)
                true
            } catch (e: Exception) {
                false
            }

    /** Check if a key is hardware-backed */
    fun isKeyHardwareBacked(keyAlias: String): Boolean =
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey ?: return false
                val keyInfo =
                        KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
                                .getKeySpec(privateKey, KeyInfo::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    keyInfo.securityLevel >= KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
                } else {
                    @Suppress("DEPRECATION") keyInfo.isInsideSecureHardware
                }
            } catch (e: Exception) {
                false
            }

    /** Check if a key is StrongBox-backed */
    @RequiresApi(Build.VERSION_CODES.S)
    fun isKeyStrongBoxBacked(keyAlias: String): Boolean =
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey ?: return false
                val keyInfo =
                        KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
                                .getKeySpec(privateKey, KeyInfo::class.java)
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } catch (e: Exception) {
                false
            }

    /** Create a signer with biometric authentication */
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
        val biometricPrompt =
                BiometricPrompt(
                        activity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationError(
                                    errorCode: Int,
                                    errString: CharSequence
                            ) {
                                continuation.resumeWithException(
                                        C2PAError.Api("Biometric authentication error: $errString")
                                )
                            }

                            override fun onAuthenticationSucceeded(
                                    result: BiometricPrompt.AuthenticationResult
                            ) {
                                try {
                                    val signer =
                                            Signer.withKeystore(
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
                        }
                )

        val promptInfo =
                BiometricPrompt.PromptInfo.Builder()
                        .setTitle(promptTitle)
                        .setSubtitle(promptSubtitle)
                        .apply { promptDescription?.let { setDescription(it) } }
                        .setNegativeButtonText("Cancel")
                        .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
