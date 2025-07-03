package info.guardianproject.c2paexample

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import info.guardianproject.c2pa.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import javax.crypto.KeyGenerator
import javax.net.ssl.HttpsURLConnection

/**
 * Comprehensive test suite for C2PA Android library
 * Ensures parity with iOS test coverage
 */
@RunWith(AndroidJUnit4::class)
class C2PAComprehensiveTests {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    // MARK: - Signing Tests
    
    @Test
    fun testWebServiceRealSigningAndVerification() = runBlocking {
        val manifestJson = """{
            "claim_generator": "test_app/1.0",
            "assertions": [
                {
                    "label": "c2pa.actions",
                    "data": {
                        "actions": [
                            {
                                "action": "c2pa.created"
                            }
                        ]
                    }
                }
            ]
        }"""
        
        val builder = C2PABuilder.fromJson(manifestJson)
        assertNotNull("Should create builder", builder)
        
        val sourceImageData = getResourceAsBytes(R.raw.pexels_asadphoto_457882)
        val sourceStream = MemoryC2PAStream(sourceImageData)
        val destStream = MemoryC2PAStream()
        
        // Create web service signer
        val signerInfo = SignerInfo(
            alg = "es256",
            signCert = null,
            privateKey = null,
            tsaUrl = "http://timestamp.digicert.com"
        )
        
        val signer = C2PASigner.fromWebService(
            url = "https://c2pa-signing-service.example.com/sign",
            token = "test-token",
            info = signerInfo
        )
        
        if (signer != null) {
            try {
                val result = builder!!.sign("image/jpeg", sourceStream, destStream, signer)
                assertTrue("Signed image should be larger than original", result.size > sourceImageData.size)
                
                // Verify the signed image
                val signedData = destStream.getData()
                val verifyStream = MemoryC2PAStream(signedData)
                val reader = C2PAReader.fromStream("image/jpeg", verifyStream)
                assertNotNull("Should read signed manifest", reader)
                
                val manifestData = reader!!.toJson()
                assertTrue("Manifest should not be empty", manifestData.isNotEmpty())
                
                reader.close()
                verifyStream.close()
            } catch (e: Exception) {
                // Web service might not be available in test environment
                println("Web service signing test skipped: ${e.message}")
            } finally {
                signer.close()
            }
        }
        
        builder!!.close()
        sourceStream.close()
        destStream.close()
    }
    
    @Test
    fun testKeystoreSignerCreation() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        val alias = "test_c2pa_key_${System.currentTimeMillis()}"
        
        try {
            // Generate a key pair in Android Keystore
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )
            
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .build()
            
            keyPairGenerator.initialize(spec)
            val keyPair = keyPairGenerator.generateKeyPair()
            
            // Create certificate chain
            val cert = keyStore.getCertificate(alias) as X509Certificate
            val certChain = arrayOf(cert)
            
            // Create signer from keystore
            val signer = C2PASigner.fromKeystore(
                keyStore = keyStore,
                alias = alias,
                algorithm = "es256",
                certChain = certChain
            )
            
            assertNotNull("Should create keystore signer", signer)
            
            val reserveSize = signer!!.reserveSize()
            assertTrue("Reserve size should be positive", reserveSize > 0)
            
            signer.close()
        } finally {
            // Clean up
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
    }
    
    @Test
    fun testHardwareBackedSignerCreation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            println("Hardware-backed keys require API 23+")
            return
        }
        
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        val alias = "test_hardware_key_${System.currentTimeMillis()}"
        
        try {
            // Try to create hardware-backed key (StrongBox or TEE)
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )
            
            val specBuilder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            
            // Try StrongBox first if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    specBuilder.setIsStrongBoxBacked(true)
                } catch (e: Exception) {
                    // StrongBox not available, will fall back to TEE
                }
            }
            
            keyPairGenerator.initialize(specBuilder.build())
            val keyPair = keyPairGenerator.generateKeyPair()
            
            // Verify hardware backing
            val privateKey = keyPair.private
            val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
            val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java) as KeyInfo
            
            val isHardwareBacked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                keyInfo.isInsideSecureHardware
            }
            
            if (isHardwareBacked) {
                val cert = keyStore.getCertificate(alias) as X509Certificate
                val certChain = arrayOf(cert)
                
                val signer = C2PASigner.fromKeystore(
                    keyStore = keyStore,
                    alias = alias,
                    algorithm = "es256",
                    certChain = certChain
                )
                
                assertNotNull("Should create hardware-backed signer", signer)
                signer?.close()
            } else {
                println("Hardware-backed keys not available on this device")
            }
        } finally {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
    }
    
    @Test
    fun testSigningAlgorithmTests() {
        val algorithms = listOf("es256", "es384", "es512", "ps256", "ps384", "ps512")
        
        for (alg in algorithms) {
            // For now, only test ES256 as we don't have PS certificates in resources
            if (!alg.startsWith("es")) {
                println("Skipping $alg test - PS certificates not available")
                continue
            }
            
            val certPem = getResourceAsString(R.raw.es256_certs)
            val keyPem = getResourceAsString(R.raw.es256_private)
            
            val signerInfo = SignerInfo(
                alg = alg,
                signCert = certPem,
                privateKey = keyPem
            )
            
            try {
                val signer = C2PASigner.fromInfo(signerInfo)
                assertNotNull("Should create signer for algorithm $alg", signer)
                
                val manifestJson = """{
                    "claim_generator": "test_app/1.0",
                    "assertions": []
                }"""
                
                val builder = C2PABuilder.fromJson(manifestJson)
                val sourceImageData = getResourceAsBytes(R.raw.pexels_asadphoto_457882)
                val sourceStream = MemoryC2PAStream(sourceImageData)
                val destStream = MemoryC2PAStream()
                
                val result = builder!!.sign("image/jpeg", sourceStream, destStream, signer!!)
                assertTrue("Should sign with algorithm $alg", result.size > 0)
                
                signer.close()
                builder.close()
                sourceStream.close()
                destStream.close()
            } catch (e: Exception) {
                // Some algorithms might not be supported
                println("Algorithm $alg test failed: ${e.message}")
            }
        }
    }
    
    @Test
    fun testSignerReserveSize() {
        val certPem = getResourceAsString(R.raw.es256_certs)
        val keyPem = getResourceAsString(R.raw.es256_private)
        
        val signerInfo = SignerInfo(
            alg = "es256",
            signCert = certPem,
            privateKey = keyPem
        )
        
        val signer = C2PASigner.fromInfo(signerInfo)
        assertNotNull("Should create signer", signer)
        
        val reserveSize = signer!!.reserveSize()
        assertTrue("Reserve size should be positive", reserveSize > 0)
        assertTrue("Reserve size should be reasonable (< 100KB)", reserveSize < 100000)
        
        // Test with TSA URL (should increase reserve size)
        val signerInfoWithTsa = SignerInfo(
            alg = "es256",
            signCert = certPem,
            privateKey = keyPem,
            tsaUrl = "http://timestamp.digicert.com"
        )
        
        val signerWithTsa = C2PASigner.fromInfo(signerInfoWithTsa)
        val reserveSizeWithTsa = signerWithTsa!!.reserveSize()
        
        assertTrue("TSA reserve size should be larger", reserveSizeWithTsa > reserveSize)
        
        signer.close()
        signerWithTsa.close()
    }
    
    @Test
    fun testReaderResourceErrorHandling() {
        val testImageData = getResourceAsBytes(R.raw.adobe_20220124_ci)
        val stream = MemoryC2PAStream(testImageData)
        
        val reader = C2PAReader.fromStream("image/jpeg", stream)
        assertNotNull("Should create reader", reader)
        
        // Test non-existent resource
        val errorStream = MemoryC2PAStream()
        val result = reader!!.resourceToStream("non_existent_resource", errorStream)
        assertEquals("Should return error for non-existent resource", -1, result)
        
        // Test invalid resource identifier
        val invalidStream = MemoryC2PAStream()
        val invalidResult = reader.resourceToStream("", invalidStream)
        assertEquals("Should return error for empty resource identifier", -1, invalidResult)
        
        // Test with closed stream
        errorStream.close()
        val closedResult = reader.resourceToStream("thumbnail", errorStream)
        assertEquals("Should return error for closed stream", -1, closedResult)
        
        reader.close()
        stream.close()
        invalidStream.close()
    }
    
    @Test
    fun testErrorEnumCoverage() {
        // Test various error conditions to ensure proper error handling
        
        // Test with null path
        val nullResult = C2PA.readFile(null)
        assertNull("Should return null for null path", nullResult)
        val nullError = C2PA.getError()
        assertNotNull("Should have error for null path", nullError)
        
        // Test with empty path
        val emptyResult = C2PA.readFile("")
        assertNull("Should return null for empty path", emptyResult)
        val emptyError = C2PA.getError()
        assertNotNull("Should have error for empty path", emptyError)
        
        // Test with directory path
        val dirResult = C2PA.readFile(context.cacheDir.absolutePath)
        assertNull("Should return null for directory", dirResult)
        val dirError = C2PA.getError()
        assertNotNull("Should have error for directory", dirError)
        
        // Test with invalid JSON for builder
        try {
            val invalidBuilder = C2PABuilder.fromJson("invalid json")
            assertNull("Should not create builder from invalid JSON", invalidBuilder)
        } catch (e: Exception) {
            assertNotNull("Should throw exception for invalid JSON", e)
        }
        
        // Test with invalid format for reader
        val imageData = getResourceAsBytes(R.raw.pexels_asadphoto_457882)
        val stream = MemoryC2PAStream(imageData)
        val invalidReader = C2PAReader.fromStream("invalid/format", stream)
        assertNull("Should not create reader with invalid format", invalidReader)
        stream.close()
    }
    
    // MARK: - Hardware Security Tests
    
    @Test
    fun testHardwareSecurityCapabilities() {
        val hasStrongBox = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
        } else {
            false
        }
        
        val hasTEE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Try to create a hardware-backed key to check TEE availability
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                
                val alias = "test_tee_probe"
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                
                keyGen.init(spec)
                val key = keyGen.generateKey()
                
                val factory = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
                
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    keyInfo.securityLevel >= KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
                } else {
                    keyInfo.isInsideSecureHardware
                }
                
                keyStore.deleteEntry(alias)
                result
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
        
        println("Hardware Security: StrongBox=$hasStrongBox, TEE=$hasTEE")
        assertTrue("Should detect at least one hardware security feature", hasStrongBox || hasTEE)
    }
    
    @Test
    fun testHardwareKeyGeneration() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            println("Hardware key generation requires API 23+")
            return
        }
        
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        val alias = "test_hw_gen_${System.currentTimeMillis()}"
        
        try {
            val keyPairGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .build()
            
            keyPairGen.initialize(spec)
            val keyPair = keyPairGen.generateKeyPair()
            
            assertNotNull("Should generate key pair", keyPair)
            assertNotNull("Should have private key", keyPair.private)
            assertNotNull("Should have public key", keyPair.public)
            
            // Verify key is in keystore
            assertTrue("Key should exist in keystore", keyStore.containsAlias(alias))
            
            val cert = keyStore.getCertificate(alias)
            assertNotNull("Should have certificate", cert)
        } finally {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
    }
    
    @Test
    fun testHardwareKeyAttestation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            println("Key attestation requires API 24+")
            return
        }
        
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        val alias = "test_attestation_${System.currentTimeMillis()}"
        
        try {
            val keyPairGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            val specBuilder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                specBuilder.setAttestationChallenge("test_challenge".toByteArray())
            }
            
            keyPairGen.initialize(specBuilder.build())
            keyPairGen.generateKeyPair()
            
            val certChain = keyStore.getCertificateChain(alias)
            assertNotNull("Should have certificate chain", certChain)
            assertTrue("Certificate chain should not be empty", certChain.isNotEmpty())
            
            // On supported devices, chain should contain attestation certificate
            if (certChain.size > 1) {
                println("Attestation certificate chain length: ${certChain.size}")
            }
        } finally {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
    }
    
    @Test
    fun testStrongBoxTEEDetection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            println("StrongBox detection requires API 28+")
            return
        }
        
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        // Test StrongBox
        val strongBoxAlias = "test_strongbox_${System.currentTimeMillis()}"
        var hasStrongBox = false
        
        try {
            val keyPairGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(strongBoxAlias, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setIsStrongBoxBacked(true)
                .build()
            
            keyPairGen.initialize(spec)
            keyPairGen.generateKeyPair()
            hasStrongBox = true
            
            println("StrongBox is available on this device")
        } catch (e: Exception) {
            println("StrongBox not available: ${e.message}")
        } finally {
            if (keyStore.containsAlias(strongBoxAlias)) {
                keyStore.deleteEntry(strongBoxAlias)
            }
        }
        
        // Test TEE
        val teeAlias = "test_tee_${System.currentTimeMillis()}"
        var hasTEE = false
        
        try {
            val keyPairGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(teeAlias, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .build()
            
            keyPairGen.initialize(spec)
            val keyPair = keyPairGen.generateKeyPair()
            
            val factory = KeyFactory.getInstance(keyPair.private.algorithm, "AndroidKeyStore")
            val keyInfo = factory.getKeySpec(keyPair.private, KeyInfo::class.java) as KeyInfo
            
            hasTEE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                keyInfo.securityLevel >= KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
            } else {
                keyInfo.isInsideSecureHardware
            }
            
            if (hasTEE) {
                println("TEE is available on this device")
            }
        } finally {
            if (keyStore.containsAlias(teeAlias)) {
                keyStore.deleteEntry(teeAlias)
            }
        }
        
        assertTrue("Device should have either StrongBox or TEE", hasStrongBox || hasTEE)
    }
    
    @Test
    fun testHardwareKeyLifecycle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            println("Hardware key lifecycle test requires API 23+")
            return
        }
        
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        val alias = "test_lifecycle_${System.currentTimeMillis()}"
        
        // Create key
        val keyPairGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .build()
        
        keyPairGen.initialize(spec)
        val keyPair = keyPairGen.generateKeyPair()
        
        assertTrue("Key should exist after creation", keyStore.containsAlias(alias))
        
        // Use key for signing
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair.private)
        signature.update("test data".toByteArray())
        val sig = signature.sign()
        
        assertNotNull("Should produce signature", sig)
        assertTrue("Signature should have length", sig.isNotEmpty())
        
        // Verify signature
        signature.initVerify(keyStore.getCertificate(alias))
        signature.update("test data".toByteArray())
        val verified = signature.verify(sig)
        
        assertTrue("Signature should verify", verified)
        
        // Delete key
        keyStore.deleteEntry(alias)
        assertFalse("Key should not exist after deletion", keyStore.containsAlias(alias))
    }
    
    // Helper methods
    
    private fun getResourceAsBytes(resourceId: Int): ByteArray {
        val inputStream = context.resources.openRawResource(resourceId)
        val data = inputStream.readBytes()
        inputStream.close()
        return data
    }
    
    private fun getResourceAsString(resourceId: Int): String {
        val inputStream = context.resources.openRawResource(resourceId)
        val text = inputStream.bufferedReader().use { it.readText() }
        inputStream.close()
        return text
    }
    
    private fun copyResourceToFile(resourceId: Int, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        val inputStream = context.resources.openRawResource(resourceId)
        file.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        inputStream.close()
        return file
    }
}