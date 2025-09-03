package org.contentauth.c2pa.signingserver.services

import kotlinx.datetime.Clock
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.Signer
import org.contentauth.c2pa.SigningAlgorithm
import org.contentauth.c2pa.signingserver.MemoryStream
import org.contentauth.c2pa.signingserver.models.C2PASigningResponse
import org.contentauth.c2pa.signingserver.models.SigningSignatureInfo
import java.io.StringWriter
import java.security.PrivateKey
import java.security.Security

class
C2PASigningService(
    private val certificateService: CertificateSigningService
) {

    companion object {
        init {
            loadNativeLibraries()
        }

        private fun loadNativeLibraries() {
            // Load the native libraries from the compiled location
            val workingDir = System.getProperty("user.dir")
            println("[C2PA Server] Working directory: $workingDir")

            // First load the C2PA C library
            val c2paLibFile = java.io.File(workingDir, "libs/libc2pa_c.dylib")
            if (c2paLibFile.exists()) {
                System.load(c2paLibFile.absolutePath)
                println("[C2PA Server] Loaded C2PA C library from: ${c2paLibFile.absolutePath}")
            }

            // Then load the JNI library
            val jniLibFile = java.io.File(workingDir, "libs/libc2pa_server_jni.dylib")
            if (jniLibFile.exists()) {
                System.load(jniLibFile.absolutePath)
                println("[C2PA Server] Loaded JNI library from: ${jniLibFile.absolutePath}")
            } else {
                throw RuntimeException("Cannot find JNI library at: ${jniLibFile.absolutePath}")
            }
        }
    }

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    suspend fun signManifest(
        manifestJSON: String,
        imageData: ByteArray,
        format: String
    ): C2PASigningResponse {
        // Use the actual C2PA signing implementation with test certificates
        val signedData = signWithTestCerts(manifestJSON, imageData, format)

        return C2PASigningResponse(
            manifestStore = signedData,
            signatureInfo = SigningSignatureInfo(
                algorithm = "ES256", // P-256 with SHA-256
                certificateChain = null, // Certificate chain is embedded in the manifest
                timestamp = Clock.System.now()
            )
        )
    }

    private fun signWithTestCerts(
        manifestJSON: String,
        imageData: ByteArray,
        format: String
    ): ByteArray {
        // Load test certificates from resources
        val certificateChain = Thread.currentThread().contextClassLoader
            .getResourceAsStream("certs/es256_certs.pem")?.reader()?.readText()
            ?: throw IllegalStateException("Cannot load certificate")
        val privateKey = Thread.currentThread().contextClassLoader
            .getResourceAsStream("certs/es256_private.key")?.reader()?.readText()
            ?: throw IllegalStateException("Cannot load private key")

        println("[C2PA Server] Creating signer with manifest JSON:")
        println(manifestJSON.take(200) + if (manifestJSON.length > 200) "..." else "")
        println("[C2PA Server] Certificate chain length: ${certificateChain.length}")
        println("[C2PA Server] Image size: ${imageData.size} bytes, format: $format")

        // Use the C2PA API with Signer
        val signer = Signer.fromKeys(
            certsPEM = certificateChain,
            privateKeyPEM = privateKey,
            algorithm = SigningAlgorithm.ES256,
            tsaURL = null
        )

        val builder = Builder.fromJson(manifestJSON)

        // Use MemoryStream like the tests do
        val sourceStream = MemoryStream(imageData)
        val destStream = MemoryStream()

        try {
            val result = builder.sign(
                format = format.substringAfter('/'), // "jpeg" from "image/jpeg"
                source = sourceStream.stream,
                dest = destStream.stream,
                signer = signer
            )

            println("[C2PA Server] Signing completed, size: ${result.size}")
            // Return the signed data
            return destStream.getData()
        } catch (e: Exception) {
            println("[C2PA Server] Signing failed: ${e.message}")
            throw e
        } finally {
            signer.close()
            builder.close()
        }
    }

    suspend fun signWithCallback(
        manifestJSON: String,
        imageData: ByteArray,
        format: String,
        signingCallback: suspend (ByteArray) -> ByteArray
    ): C2PASigningResponse {
        // Generate temporary certificate for the signing operation
        val (certificateChain, _) = certificateService.generateTemporaryCertificate()

        // Mock callback signing - simulate signing operation
        val mockDataToSign = "mock_data_to_sign".toByteArray()
        val signature = signingCallback(mockDataToSign)

        // Return mock signed data
        val signedData = imageData

        return C2PASigningResponse(
            manifestStore = signedData,
            signatureInfo = SigningSignatureInfo(
                algorithm = "ES256",
                certificateChain = certificateChain,
                timestamp = Clock.System.now()
            )
        )
    }

    suspend fun signWithWebService(
        manifestJSON: String,
        imageData: ByteArray,
        format: String,
        webServiceUrl: String,
        apiKey: String? = null
    ): C2PASigningResponse {
        // Mock web service signing
        val signedData = imageData

        return C2PASigningResponse(
            manifestStore = signedData,
            signatureInfo = SigningSignatureInfo(
                algorithm = "ES256",
                certificateChain = null, // Certificate managed by web service
                timestamp = Clock.System.now()
            )
        )
    }

    private fun privateKeyToPEM(privateKey: PrivateKey): String {
        val writer = StringWriter()
        val pemWriter = PemWriter(writer)
        val pemObject = PemObject("PRIVATE KEY", privateKey.encoded)
        pemWriter.writeObject(pemObject)
        pemWriter.close()
        return writer.toString()
    }
}
