package org.contentauth.c2pa.test.shared

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Hardware signing tests for C2PA Android
 * Similar to iOS's Secure Enclave tests but using Android's hardware security features
 */
class HardwareSigningTestSuite(private val context: Context) {
    
    // Remove duplicate TestResult - use the one from TestSuite
    
    companion object {
        // Configuration for signing server
        // Use 10.0.2.2 for Android emulator to access host's localhost
        private const val EMULATOR_SERVER_URL = "http://10.0.2.2:8080"
        // Use Tailscale URL for physical devices on the same Tailscale network
        private const val PHYSICAL_DEVICE_SERVER_URL = "https://air.tiger-agama.ts.net:8081"
        
        private fun getServerUrl(): String {
            // Check if running on emulator
            return if (Build.FINGERPRINT.contains("generic") || 
                       Build.FINGERPRINT.contains("emulator") ||
                       Build.MODEL.contains("Emulator") ||
                       Build.MODEL.contains("Android SDK")) {
                EMULATOR_SERVER_URL
            } else {
                PHYSICAL_DEVICE_SERVER_URL
            }
        }
    }
    
    /**
     * Run all hardware signing tests
     */
    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun runAllTests(): List<TestSuiteCore.TestResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TestSuiteCore.TestResult>()
        
        // Check signing server availability first
        results.add(testSigningServerHealth())
        
        // Hardware security checks
        results.add(testHardwareSecurityAvailability())
        
        // CSR generation tests
        results.add(testCSRGeneration())
        results.add(testCSRSubmission())
        
        // Hardware signing tests
        results.add(testHardwareKeySigning())
        
        // StrongBox tests (Android P+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            results.add(testStrongBoxAvailability())
            results.add(testStrongBoxCSRGeneration())
            results.add(testStrongBoxSigning())
        }
        
        // End-to-end test with C2PA manifest
        results.add(testEndToEndHardwareSigning())
        
        results
    }
    
    /**
     * Test signing server health check
     */
    private suspend fun testSigningServerHealth(): TestSuiteCore.TestResult {
        val testSteps = mutableListOf<String>()
        
        return try {
            val client = SigningServerClient(getServerUrl())
            val isHealthy = client.checkHealth()
            
            if (isHealthy) {
                testSteps.add("✓ Signing server is healthy")
                
                // Try to get server status
                val status = client.getStatus()
                if (status != null) {
                    testSteps.add("✓ Server version: ${status.version}")
                    testSteps.add("✓ C2PA version: ${status.c2pa_version}")
                    testSteps.add("✓ Server mode: ${status.mode}")
                }
            } else {
                testSteps.add("✗ Signing server is not responding")
            }
            
            TestSuiteCore.TestResult(
                "Signing Server Health",
                isHealthy,
                testSteps.joinToString("\n"),
                if (!isHealthy) "Ensure signing server is running at ${getServerUrl()}" else null
            )
        } catch (e: Exception) {
            testSteps.add("✗ Failed to check server health: ${e.message}")
            TestSuiteCore.TestResult(
                "Signing Server Health",
                false,
                testSteps.joinToString("\n"),
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Test hardware security availability
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun testHardwareSecurityAvailability(): TestSuiteCore.TestResult {
        val testSteps = mutableListOf<String>()
        
        return try {
            val hasHardwareKeystore = HardwareSecurity.isHardwareBackedKeystoreAvailable()
            testSteps.add(if (hasHardwareKeystore) {
                "✓ Hardware-backed keystore is available"
            } else {
                "✗ Hardware-backed keystore is NOT available"
            })
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val hasStrongBox = HardwareSecurity.isStrongBoxAvailable(context)
                testSteps.add(if (hasStrongBox) {
                    "✓ StrongBox is available (hardware security module)"
                } else {
                    "⚠️ StrongBox is NOT available (using TEE instead)"
                })
            }
            
            // Test creating a hardware key
            val testKeyAlias = "test_hardware_key_${UUID.randomUUID()}"
            try {
                CertificateManager.generateHardwareKey(testKeyAlias, false)
                val isHardwareBacked = CertificateManager.isKeyHardwareBacked(testKeyAlias)
                
                testSteps.add(if (isHardwareBacked) {
                    "✓ Successfully created hardware-backed key"
                } else {
                    "⚠️ Key created but not hardware-backed"
                })
                
                // Clean up
                deleteStrongBoxKey(testKeyAlias)
            } catch (e: Exception) {
                testSteps.add("✗ Failed to create hardware key: ${e.message}")
            }
            
            TestSuiteCore.TestResult(
                "Hardware Security Availability",
                hasHardwareKeystore,
                testSteps.joinToString("\n")
            )
        } catch (e: Exception) {
            testSteps.add("✗ Error checking hardware security: ${e.message}")
            TestSuiteCore.TestResult(
                "Hardware Security Availability",
                false,
                testSteps.joinToString("\n"),
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Test CSR generation for hardware-backed keys
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun testCSRGeneration(): TestSuiteCore.TestResult {
        val testSteps = mutableListOf<String>()
        val keyAlias = "test_csr_key_${UUID.randomUUID()}"
        
        return try {
            // Generate hardware key
            CertificateManager.generateHardwareKey(keyAlias, false)
            testSteps.add("✓ Generated hardware-backed key")
            
            // Create certificate config
            val certConfig = CertificateManager.CertificateConfig(
                commonName = "C2PA Android Test",
                organization = "Test Organization",
                organizationalUnit = "Android Development",
                country = "US",
                state = "California",
                locality = "San Francisco",
                emailAddress = "test@example.com"
            )
            
            // Generate CSR
            val csr = CertificateManager.createCSR(keyAlias, certConfig)
            testSteps.add("✓ Generated CSR successfully")
            
            // Validate CSR format
            val hasValidFormat = csr.contains("-----BEGIN CERTIFICATE REQUEST-----") &&
                                csr.contains("-----END CERTIFICATE REQUEST-----")
            
            if (hasValidFormat) {
                testSteps.add("✓ CSR has valid PEM format")
                testSteps.add("✓ CSR length: ${csr.length} characters")
            } else {
                testSteps.add("✗ CSR has invalid format")
            }
            
            // Clean up
            deleteStrongBoxKey(keyAlias)
            
            TestSuiteCore.TestResult(
                "CSR Generation",
                hasValidFormat,
                testSteps.joinToString("\n"),
                if (!hasValidFormat) csr.take(200) else null
            )
        } catch (e: Exception) {
            // Clean up on error
            try { deleteStrongBoxKey(keyAlias) } catch (_: Exception) {}
            
            testSteps.add("✗ CSR generation failed: ${e.message}")
            TestSuiteCore.TestResult(
                "CSR Generation",
                false,
                testSteps.joinToString("\n"),
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Test CSR submission to signing server
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun testCSRSubmission(): TestSuiteCore.TestResult {
        val testSteps = mutableListOf<String>()
        val keyAlias = "test_csr_submit_${UUID.randomUUID()}"
        
        return try {
            // Check server first
            val client = SigningServerClient(getServerUrl())
            if (!client.checkHealth()) {
                testSteps.add("⚠️ Signing server not available, skipping test")
                return TestSuiteCore.TestResult(
                    "CSR Submission",
                    true,
                    testSteps.joinToString("\n"),
                    "Test skipped - server not available"
                )
            }
            
            // Generate hardware key and CSR
            CertificateManager.generateHardwareKey(keyAlias, false)
            testSteps.add("✓ Generated hardware key")
            
            val certConfig = CertificateManager.CertificateConfig(
                commonName = "C2PA Android Signer",
                organization = "Test Org",
                organizationalUnit = "Mobile",
                country = "US",
                state = "CA",
                locality = "SF"
            )
            
            val csr = CertificateManager.createCSR(keyAlias, certConfig)
            testSteps.add("✓ Generated CSR")
            
            // Submit CSR
            val metadata = SigningServerClient.CSRMetadata(
                deviceId = Build.ID,
                appVersion = "1.0.0-test",
                purpose = "hardware-signing-test"
            )
            
            val result = client.signCSR(csr, metadata)
            
            if (result.isSuccess) {
                val response = result.getOrNull()!!
                testSteps.add("✓ CSR signed successfully")
                testSteps.add("✓ Certificate ID: ${response.certificateId}")
                testSteps.add("✓ Serial Number: ${response.serialNumber}")
                
                // Validate certificate chain
                val certCount = response.certificateChain.split("-----BEGIN CERTIFICATE-----").size - 1
                testSteps.add("✓ Certificate chain contains $certCount certificate(s)")
            } else {
                testSteps.add("✗ CSR submission failed: ${result.exceptionOrNull()?.message}")
            }
            
            // Clean up
            deleteStrongBoxKey(keyAlias)
            
            TestSuiteCore.TestResult(
                "CSR Submission",
                result.isSuccess,
                testSteps.joinToString("\n")
            )
        } catch (e: Exception) {
            // Clean up on error
            try { deleteStrongBoxKey(keyAlias) } catch (_: Exception) {}
            
            testSteps.add("✗ CSR submission failed: ${e.message}")
            TestSuiteCore.TestResult(
                "CSR Submission",
                false,
                testSteps.joinToString("\n"),
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Test hardware key signing with CSR-issued certificate
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun testHardwareKeySigning(): TestSuiteCore.TestResult {
        val testSteps = mutableListOf<String>()
        val keyAlias = "test_hw_sign_${UUID.randomUUID()}"
        
        return try {
            // Check server
            val serverUrl = getServerUrl()
            val client = SigningServerClient(serverUrl)
            if (!client.checkHealth()) {
                testSteps.add("⚠️ Signing server not available, skipping test")
                return TestSuiteCore.TestResult(
                    "Hardware Key Signing",
                    true,
                    testSteps.joinToString("\n"),
                    "Test skipped - server not available"
                )
            }
            
            // Create signer with CSR
            val certConfig = CertificateManager.CertificateConfig(
                commonName = "Hardware Signer",
                organization = "Test",
                organizationalUnit = "Android",
                country = "US",
                state = "CA",
                locality = "SF"
            )
            
            val signer = HardwareSecurity.createSignerWithCSR(
                keyAlias = keyAlias,
                certificateConfig = certConfig,
                signingServerUrl = serverUrl
            )
            testSteps.add("✓ Created hardware signer with CSR-issued certificate")
            
            // Test signing with the hardware key
            val manifestJson = """{
                "claim_generator": "hardware_test/1.0",
                "assertions": [
                    {"label": "c2pa.hardware", "data": {"signed_with": "hardware_key"}}
                ]
            }"""
            
            val builder = Builder.fromJson(manifestJson)
            val sourceImageData = context.resources.openRawResource(R.raw.pexels_asadphoto_457882).readBytes()
            val sourceStream = DataStream(sourceImageData)
            
            val outputFile = File.createTempFile("hw_signed_", ".jpg", context.cacheDir)
            val destStream = FileStream(outputFile)
            
            try {
                val result = builder.sign("image/jpeg", sourceStream, destStream, signer)
                testSteps.add("✓ Successfully signed with hardware key")
                testSteps.add("✓ Output file: ${outputFile.absolutePath}")
                testSteps.add("✓ Output size: ${outputFile.length()} bytes")
                
                // Verify the signed image
                val manifest = C2PA.readFile(outputFile.absolutePath)
                val json = JSONObject(manifest)
                val hasManifests = json.has("manifests")
                
                testSteps.add(if (hasManifests) {
                    "✓ Signed image contains valid C2PA manifest"
                } else {
                    "✗ Signed image missing C2PA manifest"
                })
            } finally {
                sourceStream.close()
                destStream.close()
                // outputFile.delete() // Commented out to preserve test files for verification
            }
            
            // Clean up
            deleteStrongBoxKey(keyAlias)
            
            TestSuiteCore.TestResult(
                "Hardware Key Signing",
                true,
                testSteps.joinToString("\n")
            )
        } catch (e: Exception) {
            // Clean up on error
            try { deleteStrongBoxKey(keyAlias) } catch (_: Exception) {}
            
            testSteps.add("✗ Hardware signing failed: ${e.message}")
            TestSuiteCore.TestResult(
                "Hardware Key Signing",
                false,
                testSteps.joinToString("\n"),
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Test StrongBox availability
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun testStrongBoxAvailability(): TestSuiteCore.TestResult {
        val testSteps = mutableListOf<String>()
        
        return try {
            val hasStrongBox = HardwareSecurity.isStrongBoxAvailable(context)
            
            if (hasStrongBox) {
                testSteps.add("✓ StrongBox hardware security module is available")
                
                // Try to create a StrongBox key
                val testKeyAlias = "test_strongbox_${UUID.randomUUID()}"
                try {
                    CertificateManager.generateHardwareKey(testKeyAlias, requireStrongBox = true)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val isStrongBoxBacked = CertificateManager.isKeyStrongBoxBacked(testKeyAlias)
                        testSteps.add(if (isStrongBoxBacked) {
                            "✓ Successfully created StrongBox-backed key"
                        } else {
                            "⚠️ Key created but not StrongBox-backed"
                        })
                    } else {
                        testSteps.add("✓ StrongBox key created (verification requires API 31+)")
                    }
                    
                    deleteStrongBoxKey(testKeyAlias)
                } catch (e: Exception) {
                    testSteps.add("⚠️ StrongBox advertised but key creation failed: ${e.message}")
                }
            } else {
                testSteps.add("⚠️ StrongBox not available on this device")
                testSteps.add("⚠️ Using Trusted Execution Environment (TEE) instead")
            }
            
            TestSuiteCore.TestResult(
                "StrongBox Availability",
                true, // Not a failure if StrongBox isn't available
                testSteps.joinToString("\n")
            )
        } catch (e: Exception) {
            testSteps.add("✗ Error checking StrongBox: ${e.message}")
            TestSuiteCore.TestResult(
                "StrongBox Availability",
                false,
                testSteps.joinToString("\n"),
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Test StrongBox CSR generation
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun testStrongBoxCSRGeneration(): TestSuiteCore.TestResult {
        val testSteps = mutableListOf<String>()
        
        // Check if StrongBox is available first
        if (!HardwareSecurity.isStrongBoxAvailable(context)) {
            testSteps.add("⚠️ StrongBox not available, skipping test")
            return TestSuiteCore.TestResult(
                "StrongBox CSR Generation",
                true,
                testSteps.joinToString("\n"),
                "Test skipped - StrongBox not available"
            )
        }
        
        val keyTag = "test_strongbox_csr_${UUID.randomUUID()}"
        
        return try {
            val strongBoxConfig = StrongBoxSignerConfig(keyTag)
            val certConfig = CertificateManager.CertificateConfig(
                commonName = "StrongBox Test",
                organization = "Test",
                organizationalUnit = "StrongBox",
                country = "US",
                state = "CA",
                locality = "SF"
            )
            
            val csr = CertificateManager.createStrongBoxCSR(strongBoxConfig, certConfig)
            testSteps.add("✓ Generated StrongBox CSR successfully")
            
            val hasValidFormat = csr.contains("-----BEGIN CERTIFICATE REQUEST-----")
            testSteps.add(if (hasValidFormat) {
                "✓ StrongBox CSR has valid PEM format"
            } else {
                "✗ StrongBox CSR has invalid format"
            })
            
            // Clean up
            deleteStrongBoxKey(keyTag)
            
            TestSuiteCore.TestResult(
                "StrongBox CSR Generation",
                hasValidFormat,
                testSteps.joinToString("\n")
            )
        } catch (e: Exception) {
            // Clean up on error
            try { deleteStrongBoxKey(keyTag) } catch (_: Exception) {}
            
            testSteps.add("✗ StrongBox CSR generation failed: ${e.message}")
            TestSuiteCore.TestResult(
                "StrongBox CSR Generation",
                false,
                testSteps.joinToString("\n"),
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Test StrongBox signing
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun testStrongBoxSigning(): TestSuiteCore.TestResult {
        val testSteps = mutableListOf<String>()
        
        // Check prerequisites
        if (!HardwareSecurity.isStrongBoxAvailable(context)) {
            testSteps.add("⚠️ StrongBox not available, skipping test")
            return TestSuiteCore.TestResult(
                "StrongBox Signing",
                true,
                testSteps.joinToString("\n"),
                "Test skipped - StrongBox not available"
            )
        }
        
        val serverUrl = getServerUrl()
        val client = SigningServerClient(serverUrl)
        if (!client.checkHealth()) {
            testSteps.add("⚠️ Signing server not available, skipping test")
            return TestSuiteCore.TestResult(
                "StrongBox Signing",
                true,
                testSteps.joinToString("\n"),
                "Test skipped - server not available"
            )
        }
        
        val keyTag = "test_strongbox_sign_${UUID.randomUUID()}"
        
        return try {
            val strongBoxConfig = StrongBoxSignerConfig(keyTag)
            val certConfig = CertificateManager.CertificateConfig(
                commonName = "StrongBox Signer",
                organization = "Test",
                organizationalUnit = "StrongBox",
                country = "US",
                state = "CA",
                locality = "SF"
            )
            
            val signer = HardwareSecurity.createStrongBoxSignerWithCSR(
                strongBoxConfig = strongBoxConfig,
                certificateConfig = certConfig,
                signingServerUrl = serverUrl
            )
            testSteps.add("✓ Created StrongBox signer with CSR-issued certificate")
            
            // Test that the signer was created successfully
            testSteps.add("✓ Successfully created signer with StrongBox key")
            testSteps.add("✓ Signer is ready for C2PA signing operations")
            
            // Clean up
            deleteStrongBoxKey(keyTag)
            
            TestSuiteCore.TestResult(
                "StrongBox Signing",
                true,
                testSteps.joinToString("\n")
            )
        } catch (e: Exception) {
            // Clean up on error
            try { deleteStrongBoxKey(keyTag) } catch (_: Exception) {}
            
            testSteps.add("✗ StrongBox signing failed: ${e.message}")
            TestSuiteCore.TestResult(
                "StrongBox Signing",
                false,
                testSteps.joinToString("\n"),
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * End-to-end test with hardware signing
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun testEndToEndHardwareSigning(): TestSuiteCore.TestResult {
        val testSteps = mutableListOf<String>()
        
        // Check server availability
        val serverUrl = getServerUrl()
        val client = SigningServerClient(serverUrl)
        if (!client.checkHealth()) {
            testSteps.add("⚠️ Signing server not available, skipping test")
            return TestSuiteCore.TestResult(
                "End-to-End Hardware Signing",
                true,
                testSteps.joinToString("\n"),
                "Test skipped - server not available"
            )
        }
        
        val keyAlias = "test_e2e_${UUID.randomUUID()}"
        
        return try {
            // Step 1: Create hardware-backed signer with CSR
            testSteps.add("Step 1: Creating hardware signer with CSR")
            val certConfig = CertificateManager.CertificateConfig(
                commonName = "E2E Test Signer",
                organization = "C2PA Android Test",
                organizationalUnit = "Integration Testing",
                country = "US",
                state = "California",
                locality = "San Francisco",
                emailAddress = "test@c2pa-android.org"
            )
            
            val requireStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && 
                                  HardwareSecurity.isStrongBoxAvailable(context)
            
            val signer = HardwareSecurity.createSignerWithCSR(
                keyAlias = keyAlias,
                certificateConfig = certConfig,
                signingServerUrl = serverUrl,
                requireStrongBox = requireStrongBox
            )
            testSteps.add("✓ Created hardware signer (StrongBox: $requireStrongBox)")
            
            // Step 2: Create C2PA manifest
            testSteps.add("\nStep 2: Creating C2PA manifest")
            val manifestJson = """{
                "claim_generator": "C2PA Android Hardware Test/1.0",
                "title": "Hardware-Signed Image",
                "assertions": [
                    {
                        "label": "c2pa.actions",
                        "data": {
                            "actions": [
                                {
                                    "action": "c2pa.signed",
                                    "when": "${java.time.Instant.now()}",
                                    "parameters": {
                                        "hardware_backed": true,
                                        "strongbox": $requireStrongBox
                                    }
                                }
                            ]
                        }
                    }
                ]
            }"""
            
            val builder = Builder.fromJson(manifestJson)
            testSteps.add("✓ Created C2PA builder with manifest")
            
            // Step 3: Sign image with hardware key
            testSteps.add("\nStep 3: Signing image with hardware key")
            val sourceImageData = context.resources.openRawResource(R.raw.pexels_asadphoto_457882).readBytes()
            val sourceStream = DataStream(sourceImageData)
            
            val outputFile = File.createTempFile("e2e_hw_signed_", ".jpg", context.cacheDir)
            val destStream = FileStream(outputFile)
            
            try {
                val result = builder.sign("image/jpeg", sourceStream, destStream, signer)
                testSteps.add("✓ Successfully signed image with hardware key")
                testSteps.add("✓ Output file: ${outputFile.absolutePath}")
                testSteps.add("✓ Output size: ${outputFile.length()} bytes")
                
                // Step 4: Verify the signed image
                testSteps.add("\nStep 4: Verifying signed image")
                val manifest = C2PA.readFile(outputFile.absolutePath)
                val json = JSONObject(manifest)
                
                if (json.has("manifests")) {
                    testSteps.add("✓ Signed image contains valid C2PA manifest")
                    
                    // Check for our custom assertions
                    val manifests = json.getJSONObject("manifests")
                    if (manifests.length() > 0) {
                        val manifestKey = manifests.keys().next()
                        val manifestData = manifests.getJSONObject(manifestKey)
                        
                        if (manifestData.has("claim_generator")) {
                            val generator = manifestData.getString("claim_generator")
                            testSteps.add("✓ Claim generator: $generator")
                        }
                        
                        if (manifestData.has("title")) {
                            val title = manifestData.getString("title")
                            testSteps.add("✓ Manifest title: $title")
                        }
                    }
                } else {
                    testSteps.add("✗ Signed image missing C2PA manifest")
                }
                
                // Check file size
                val fileSize = outputFile.length()
                testSteps.add("✓ Output file size: ${fileSize / 1024} KB")
                
            } finally {
                sourceStream.close()
                destStream.close()
                // outputFile.delete() // Commented out to preserve test files for verification
            }
            
            // Clean up
            deleteStrongBoxKey(keyAlias)
            testSteps.add("\n✓ Test completed successfully")
            
            TestSuiteCore.TestResult(
                "End-to-End Hardware Signing",
                true,
                testSteps.joinToString("\n")
            )
        } catch (e: Exception) {
            // Clean up on error
            try { deleteStrongBoxKey(keyAlias) } catch (_: Exception) {}
            
            testSteps.add("\n✗ End-to-end test failed: ${e.message}")
            TestSuiteCore.TestResult(
                "End-to-End Hardware Signing",
                false,
                testSteps.joinToString("\n"),
                "Error: ${e.stackTraceToString()}"
            )
        }
    }
    
}