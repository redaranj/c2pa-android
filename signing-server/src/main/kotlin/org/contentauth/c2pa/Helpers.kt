package org.contentauth.c2pa

/**
 * Load native C2PA libraries for server environment.
 * Safe to call multiple times - System.loadLibrary handles duplicates.
 */
internal fun loadC2PALibraries() {
    try {
        // JVM (signing server): Load from file system using system properties
        val c2paServerLib = System.getProperty("c2pa.server.lib.path") 
            ?: throw UnsatisfiedLinkError("c2pa.server.lib.path system property not set")
        val c2paServerJni = System.getProperty("c2pa.server.jni.path")
            ?: throw UnsatisfiedLinkError("c2pa.server.jni.path system property not set")
        
        System.load(c2paServerLib)
        System.load(c2paServerJni)
    } catch (e: UnsatisfiedLinkError) {
        // Libraries might already be loaded, ignore
    }
}

/**
 * Execute a C2PA operation with standard error handling
 */
internal inline fun <T : Any> executeC2PAOperation(
    errorMessage: String,
    operation: () -> T?
): T {
    return try {
        operation() ?: throw C2PAError.Api(C2PA.getError() ?: errorMessage)
    } catch (e: IllegalArgumentException) {
        throw C2PAError.Api(e.message ?: "Invalid arguments")
    } catch (e: RuntimeException) {
        val error = C2PA.getError()
        if (error != null) {
            throw C2PAError.Api(error)
        }
        throw C2PAError.Api(e.message ?: "Runtime error")
    }
}

/**
 * C2PA version fetched once
 */
val c2paVersion: String by lazy {
    C2PA.version()
}