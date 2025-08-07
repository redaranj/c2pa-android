package org.contentauth.c2pa.test.shared

import org.contentauth.c2pa.*

/**
 * Mock signing service for Android testing (avoids socket permissions)
 * Shared between instrumented tests and test app
 */
class MockSigningService(
    private val signingFunction: (ByteArray) -> ByteArray
) {
    fun handleRequest(requestId: String, data: ByteArray): ByteArray {
        // Simulate async processing
        return signingFunction(data)
    }
    
    companion object {
        private val activeServices = mutableMapOf<String, MockSigningService>()
        
        fun register(url: String, service: MockSigningService) {
            activeServices[url] = service
        }
        
        fun unregister(url: String) {
            activeServices.remove(url)
        }
        
        fun getService(url: String): MockSigningService? {
            return activeServices[url]
        }
    }
}

/**
 * Helper object for creating web service signers
 */
object WebServiceSignerHelper {
    fun createWebServiceSigner(
        serviceUrl: String,
        algorithm: SigningAlgorithm,
        certsPem: String,
        tsaUrl: String? = null
    ): Signer {
        // Use MockSigningService for the actual signing
        val mockService = MockSigningService.getService(serviceUrl)
            ?: throw IllegalStateException("No mock service registered for $serviceUrl")
        
        return Signer.withWebService(
            algorithm = algorithm,
            certificateChainPEM = certsPem,
            tsaURL = tsaUrl,
            signer = { data -> mockService.handleRequest("test", data) }
        )
    }
}