package org.contentauth.c2pa

import org.junit.Test
import org.junit.Assert.*

class C2PAUnitTest {
    
    @Test
    fun testSigningAlgorithmValues() {
        // Test that all expected algorithms are present
        val algorithms = SigningAlgorithm.values().map { it.name }
        
        assertTrue("Should contain ES256", algorithms.contains("ES256"))
        assertTrue("Should contain ES384", algorithms.contains("ES384"))
        assertTrue("Should contain ES512", algorithms.contains("ES512"))
        assertTrue("Should contain PS256", algorithms.contains("PS256"))
        assertTrue("Should contain PS384", algorithms.contains("PS384"))
        assertTrue("Should contain PS512", algorithms.contains("PS512"))
        assertTrue("Should contain ED25519", algorithms.contains("ED25519"))
        
        // Also test the description property returns lowercase
        assertEquals("es256", SigningAlgorithm.ES256.description)
    }
    
    @Test
    fun testSeekModeValues() {
        assertEquals(0, SeekMode.START.value)
        assertEquals(1, SeekMode.CURRENT.value)
        assertEquals(2, SeekMode.END.value)
    }
    
    @Test
    fun testErrorTypes() {
        val apiError = C2PAError.Api("Test message")
        assertEquals("Test message", apiError.message)
        assertTrue(apiError.toString().contains("Test message"))
        
        val negativeError = C2PAError.Negative(-100)
        assertEquals(-100, negativeError.value)
        assertTrue(negativeError.toString().contains("-100"))
    }
    
    @Test
    fun testSignerInfoCreation() {
        val info = SignerInfo(
            algorithm = SigningAlgorithm.ES256,
            certificatePEM = "test_cert",
            privateKeyPEM = "test_key",
            tsaURL = "https://example.com/tsa"
        )
        
        assertEquals(SigningAlgorithm.ES256, info.algorithm)
        assertEquals("test_cert", info.certificatePEM)
        assertEquals("test_key", info.privateKeyPEM)
        assertEquals("https://example.com/tsa", info.tsaURL)
    }
}