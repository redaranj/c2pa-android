package org.contentauth.c2pa.testapp

import java.util.concurrent.ConcurrentHashMap

/**
 * Mock signing service for Android testing (avoids socket permissions)
 */
class MockSigningService(
    private val signingFunction: (ByteArray) -> ByteArray
) {
    private val requests = ConcurrentHashMap<String, ByteArray>()
    
    fun handleRequest(requestId: String, data: ByteArray): ByteArray {
        // Simulate async processing
        return signingFunction(data)
    }
    
    companion object {
        private val activeServices = ConcurrentHashMap<String, MockSigningService>()
        
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