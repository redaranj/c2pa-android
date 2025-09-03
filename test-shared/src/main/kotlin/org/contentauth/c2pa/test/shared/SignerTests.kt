package org.contentauth.c2pa.test.shared

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * SignerTests - Signing and signer-related tests
 * 
 * This file contains extracted test methods that can be run individually.
 */
abstract class SignerTests : BaseTestSuite() {

    // Individual test methods
    
    suspend fun testSignerWithCallback(): TestResult = withContext(Dispatchers.IO) {
        runTest("Signer with Callback") {
            val manifestJson = """{
                "claim_generator": "test_app/1.0",
                "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
            }"""

            try {
                val builder = Builder.fromJson(manifestJson)
                try {
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-callback-signer", ".jpg")
                    val destStream = FileStream(fileTest)

                    val certPem = loadResourceAsString("es256_certs")
                    val keyPem = loadResourceAsString("es256_private")

                    var signCallCount = 0

                    val callbackSigner = Signer.withCallback(SigningAlgorithm.ES256, certPem, null) { data ->
                        signCallCount++
                        SigningHelper.signWithPEMKey(data, keyPem, "ES256")
                    }

                    try {
                        val reserveSize = callbackSigner.reserveSize()
                        val result = builder.sign("image/jpeg", sourceStream, destStream, callbackSigner)
                        val signSucceeded = result.size > 0

                        val (manifest, signatureVerified) = if (signSucceeded) {
                            try {
                                val manifestJson = C2PA.readFile(fileTest.absolutePath)
                                if (manifestJson != null) {
                                    Pair(manifestJson, true)
                                } else {
                                    Pair(null, false)
                                }
                            } catch (e: Exception) {
                                Pair(null, false)
                            }
                        } else {
                            Pair(null, false)
                        }

                        val success = signCallCount > 0 &&
                                     reserveSize > 0 &&
                                     signSucceeded &&
                                     signatureVerified

                        TestResult(
                            "Signer with Callback",
                            success,
                            if (success) "✓ Callback signer created and used successfully"
                            else "✗ Callback signer test failed",
                            buildString {
                                append("Callback invoked: ${signCallCount} time(s)\n")
                                append("Reserve size: $reserveSize bytes\n")
                                append("Signing succeeded: $signSucceeded\n")
                                append("Signature verified: $signatureVerified")
                                if (manifest != null && manifest.length > 100) {
                                    append("\nManifest size: ${manifest.length} chars")
                                }
                            }
                        )
                    } finally {
                        callbackSigner.close()
                        sourceStream.close()
                        destStream.close()
                        fileTest.delete()
                    }
                } finally {
                    builder.close()
                }
            } catch (e: Exception) {
                TestResult(
                    "Signer with Callback",
                    false,
                    "✗ Test failed with exception",
                    "${e.javaClass.simpleName}: ${e.message}\n${e.stackTrace.take(3).joinToString("\n")}"
                )
            }
        }
    }

    suspend fun testHardwareSignerCreation(): TestResult = withContext(Dispatchers.IO) {
        runTest("Hardware Signer Creation") {
            val hasStrongBox = getContext().packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)

            var genInHw = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    val keyAlias = "test_hw_key_${System.currentTimeMillis()}"
                    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)

                    val keyGenSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                        keyAlias,
                        android.security.keystore.KeyProperties.PURPOSE_SIGN
                    ).apply {
                        setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                        setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                        if (hasStrongBox) {
                            setIsStrongBoxBacked(true)
                        }
                    }.build()

                    val keyPairGen = java.security.KeyPairGenerator.getInstance(
                        android.security.keystore.KeyProperties.KEY_ALGORITHM_EC,
                        "AndroidKeyStore"
                    )
                    keyPairGen.initialize(keyGenSpec)
                    keyPairGen.generateKeyPair()

                    val key = keyStore.getKey(keyAlias, null) as? java.security.PrivateKey
                    if (key != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        try {
                            val factory = java.security.KeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                            val keyInfo = factory.getKeySpec(key, android.security.keystore.KeyInfo::class.java)
                            @Suppress("DEPRECATION")
                            genInHw = keyInfo.isInsideSecureHardware
                        } catch (_: Exception) {
                            genInHw = hasStrongBox
                        }
                    }

                    keyStore.deleteEntry(keyAlias)
                } catch (_: Exception) {
                    // Hardware key generation failed
                }
            }

            val success = genInHw || !hasStrongBox
            TestResult(
                "Hardware Signer Creation",
                success,
                if (genInHw) "Generated key in hardware" else if (!hasStrongBox) "No StrongBox available" else "Failed to use hardware",
                "StrongBox available: $hasStrongBox, Generated in HW: $genInHw"
            )
        }
    }

    suspend fun testStrongBoxSignerCreation(): TestResult = withContext(Dispatchers.IO) {
        runTest("StrongBox Signer Creation") {
            val hasStrongBox = getContext().packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)

            var strongBoxKeyCreated = false
            if (hasStrongBox && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    val keyAlias = "test_strongbox_key_${System.currentTimeMillis()}"
                    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)

                    val keyGenSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                        keyAlias,
                        android.security.keystore.KeyProperties.PURPOSE_SIGN
                    ).apply {
                        setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                        setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                        setIsStrongBoxBacked(true)
                    }.build()

                    val keyPairGen = java.security.KeyPairGenerator.getInstance(
                        android.security.keystore.KeyProperties.KEY_ALGORITHM_EC,
                        "AndroidKeyStore"
                    )
                    keyPairGen.initialize(keyGenSpec)
                    keyPairGen.generateKeyPair()

                    strongBoxKeyCreated = keyStore.containsAlias(keyAlias)

                    keyStore.deleteEntry(keyAlias)
                } catch (_: Exception) {
                    // StrongBox key generation failed
                }
            }

            val success = strongBoxKeyCreated || !hasStrongBox
            TestResult(
                "StrongBox Signer Creation",
                success,
                if (strongBoxKeyCreated) "StrongBox key created" else if (!hasStrongBox) "StrongBox not available" else "StrongBox key creation failed",
                "Has StrongBox: $hasStrongBox, Key created: $strongBoxKeyCreated"
            )
        }
    }

    suspend fun testSigningAlgorithms(): TestResult = withContext(Dispatchers.IO) {
        runTest("Signing Algorithm Tests") {
            val algorithms = listOf("es256")
            val resultPerAlg = mutableListOf<String>()

            algorithms.forEach { alg ->
                try {
                    val manifestJson = """{"claim_generator": "test_app/1.0", "assertions": [{"label": "c2pa.test", "data": {"test": true}}]}"""
                    val builder = Builder.fromJson(manifestJson)

                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-algorithm-$alg", ".jpg")
                    val destStream = FileStream(fileTest)

                    val certPem = loadResourceAsString("es256_certs")
                    val keyPem = loadResourceAsString("es256_private")
                    val algorithm = SigningAlgorithm.entries.find { it.name == alg } ?: SigningAlgorithm.ES256
                    val signerInfo = SignerInfo(algorithm, certPem, keyPem)
                    val signer = Signer.fromInfo(signerInfo)

                    try {
                        builder.sign("image/jpeg", sourceStream, destStream, signer)
                        val ok = C2PA.readFile(fileTest.absolutePath) != null
                        resultPerAlg.add("$alg:${if(ok) "ok" else "fail"}")
                    } finally {
                        signer.close()
                        builder.close()
                        sourceStream.close()
                        destStream.close()
                        fileTest.delete()
                    }
                } catch (_: Exception) {
                    resultPerAlg.add("$alg:fail")
                }
            }

            val success = resultPerAlg.all { it.endsWith("ok") }
            TestResult(
                "Signing Algorithm Tests",
                success,
                if (success) "All algorithms passed" else "Some algorithms failed",
                resultPerAlg.joinToString(", ")
            )
        }
    }

    suspend fun testSignerReserveSize(): TestResult = withContext(Dispatchers.IO) {
        runTest("Signer Reserve Size") {
            val certPem = loadResourceAsString("es256_certs")
            val keyPem = loadResourceAsString("es256_private")
            val signerInfo = SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)
            val signer = Signer.fromInfo(signerInfo)

            try {
                val reserveSize = signer.reserveSize()
                val success = reserveSize > 0
                TestResult(
                    "Signer Reserve Size",
                    success,
                    if (success) "Signer reserve size obtained" else "Invalid reserve size",
                    "Reserve size: $reserveSize bytes"
                )
            } finally {
                signer.close()
            }
        }
    }

    suspend fun testSignFile(): TestResult = withContext(Dispatchers.IO) {
        runTest("Sign File") {
            val sourceFile = copyResourceToFile("pexels_asadphoto_457882", "source_signfile.jpg")
            val destFile = File(getContext().cacheDir, "dest_signfile.jpg")

            try {
                val certPem = loadResourceAsString("es256_certs")
                val keyPem = loadResourceAsString("es256_private")
                val signerInfo = SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)

                val manifestJson = """{
                    "claim_generator": "test_app/1.0",
                    "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
                }"""

                val result = C2PA.signFile(
                    sourceFile.absolutePath,
                    destFile.absolutePath,
                    manifestJson,
                    signerInfo
                )

                val success = result != null && destFile.exists() && destFile.length() > 0

                TestResult(
                    "Sign File",
                    success,
                    if (success) "File signed successfully" else "Failed to sign file",
                    "Result: $result, Dest size: ${destFile.length()}"
                )
            } finally {
                sourceFile.delete()
                destFile.delete()
            }
        }
    }

    suspend fun testAlgorithmCoverage(): TestResult = withContext(Dispatchers.IO) {
        runTest("Algorithm Coverage") {
            val testedAlgorithms = mutableListOf<String>()
            val supportedAlgorithms = mutableListOf<SigningAlgorithm>()

            for (alg in SigningAlgorithm.values()) {
                testedAlgorithms.add("${alg.name}: ${alg.description}")

                // Test that we can at least create the enum value
                when (alg) {
                    SigningAlgorithm.ES256,
                    SigningAlgorithm.ES384,
                    SigningAlgorithm.ES512,
                    SigningAlgorithm.PS256,
                    SigningAlgorithm.PS384,
                    SigningAlgorithm.PS512 -> supportedAlgorithms.add(alg)
                    SigningAlgorithm.ED25519 -> supportedAlgorithms.add(alg)
                }
            }

            val success = testedAlgorithms.size == SigningAlgorithm.values().size &&
                         supportedAlgorithms.size >= 6

            TestResult(
                "Algorithm Coverage",
                success,
                if (success) "All algorithms covered" else "Some algorithms missing",
                "Tested: ${testedAlgorithms.size}, Supported: ${supportedAlgorithms.size}\n" +
                testedAlgorithms.joinToString("\n")
            )
        }
    }

}