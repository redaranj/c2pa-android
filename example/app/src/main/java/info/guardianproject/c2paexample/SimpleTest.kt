package info.guardianproject.c2paexample

import android.content.Context
import android.util.Log
import info.guardianproject.c2pa.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SimpleTest {
    private const val TAG = "C2PASimpleTest"
    
    suspend fun runBasicTests(context: Context): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        
        try {
            // Test 1: Get C2PA version
            val version = C2PA.version()
            results.add("✓ C2PA Version: $version")
            Log.d(TAG, "C2PA Version: $version")
            
            // Test 2: Create a simple manifest
            val manifest = """
                {
                    "claim_generator": "C2PA Android Test/1.0",
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
                }
            """.trimIndent()
            
            val builder = Builder.fromJson(manifest)
            results.add("✓ Created Builder from JSON")
            Log.d(TAG, "Created Builder successfully")
            
            // Test 3: Check hardware security capabilities
            val capabilities = C2PA.getHardwareSecurityCapabilities(context)
            results.add("✓ Hardware Security Check:")
            results.add("  - Has Secure Hardware: ${capabilities.hasSecureHardware}")
            results.add("  - Has StrongBox: ${capabilities.hasStrongBox}")
            results.add("  - Has Biometrics: ${capabilities.hasBiometrics}")
            results.add("  - Android Version: ${capabilities.androidVersion}")
            
            // Test 4: Create a simple signer (from PEM resources)
            try {
                context.assets.open("es256_certs.pem").use { certStream ->
                    val certPem = certStream.bufferedReader().readText()
                    
                    context.assets.open("es256_private.key").use { keyStream ->
                        val keyPem = keyStream.bufferedReader().readText()
                        
                        val signerInfo = SignerInfo(
                            algorithm = SigningAlgorithm.ES256,
                            certificatePEM = certPem,
                            privateKeyPEM = keyPem,
                            tsaURL = null
                        )
                        val signer = Signer.fromInfo(signerInfo)
                        results.add("✓ Created test signer")
                        Log.d(TAG, "Created signer successfully")
                    }
                }
            } catch (e: Exception) {
                results.add("⚠ Could not create test signer: ${e.message}")
                Log.w(TAG, "Signer creation failed", e)
            }
            
            // Test 5: Ed25519 signing test
            try {
                val testData = "Hello C2PA Android!".toByteArray()
                val testKey = "MC4CAQAwBQYDK2VwBCIEIGLlzyok1bQ0JTg5q3FvDJSJJpYf6xbMTfoIBaHAgBBg"
                val signature = C2PA.ed25519Sign(testData, testKey)
                if (signature != null) {
                    results.add("✓ Ed25519 signature test passed (${signature.size} bytes)")
                    Log.d(TAG, "Ed25519 signature created: ${signature.size} bytes")
                } else {
                    results.add("⚠ Ed25519 signature returned null")
                    Log.w(TAG, "Ed25519 signature returned null")
                }
            } catch (e: Exception) {
                results.add("⚠ Ed25519 signing failed: ${e.message}")
                Log.w(TAG, "Ed25519 signing failed", e)
            }
            
        } catch (e: Exception) {
            results.add("✗ Test failed with error: ${e.message}")
            Log.e(TAG, "Test failed", e)
        }
        
        results
    }
}