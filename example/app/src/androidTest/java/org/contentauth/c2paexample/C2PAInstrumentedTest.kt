package org.contentauth.c2paexample

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.contentauth.c2pa.*
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Before
import java.io.File

@RunWith(AndroidJUnit4::class)
class C2PAInstrumentedTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testLibraryVersion() {
        val version = C2PA.version()
        assertNotNull("Version should not be null", version)
        assertTrue("Version should not be empty", version.isNotEmpty())
        assertTrue("Version should contain dots", version.contains("."))
    }
    
    @Test
    fun testErrorHandling() {
        val result = C2PA.readFile("/non/existent/file.jpg")
        assertNull("Should return null for non-existent file", result)
        
        val error = C2PA.getError()
        assertNotNull("Should have error message for non-existent file", error)
    }
    
    @Test
    fun testReadManifestFromTestImage() {
        val testImageFile = copyResourceToFile(context, R.raw.adobe_20220124_ci, "test_adobe.jpg")
        
        try {
            val manifest = C2PA.readFile(testImageFile.absolutePath)
            assertNotNull("Should read manifest from Adobe test image", manifest)
            
            val json = JSONObject(manifest!!)
            assertTrue("Should contain manifests", json.has("manifests"))
        } finally {
            testImageFile.delete()
        }
    }
    
    @Test
    fun testStreamAPI() {
        val testImageData = getResourceAsBytes(context, R.raw.adobe_20220124_ci)
        val stream = MemoryC2PAStream(testImageData)
        
        try {
            val reader = C2PAReader.fromStream("image/jpeg", stream)
            assertNotNull("Should create reader from stream", reader)
            
            val json = reader!!.toJson()
            assertNotNull("Should get JSON from reader", json)
            assertTrue("JSON should not be empty", json.isNotEmpty())
            
            reader.close()
        } finally {
            stream.close()
        }
    }
    
    @Test
    fun testBuilderAPI() {
        val manifestJson = """{
            "claim_generator": "test_app/1.0",
            "assertions": [
                {
                    "label": "c2pa.test",
                    "data": {
                        "test": true
                    }
                }
            ]
        }"""
        
        val builder = C2PABuilder.fromJson(manifestJson)
        assertNotNull("Should create builder from JSON", builder)
        
        try {
            val sourceImageData = getResourceAsBytes(context, R.raw.pexels_asadphoto_457882)
            val sourceStream = MemoryC2PAStream(sourceImageData)
            val destStream = MemoryC2PAStream()
            
            try {
                val certPem = getResourceAsString(context, R.raw.es256_certs)
                val keyPem = getResourceAsString(context, R.raw.es256_private)
                
                val signerInfo = SignerInfo(
                    alg = "es256",
                    signCert = certPem,
                    privateKey = keyPem
                )
                
                val signer = C2PASigner.fromInfo(signerInfo)
                assertNotNull("Should create signer", signer)
                
                try {
                    val result = builder!!.sign("image/jpeg", sourceStream, destStream, signer!!)
                    assertTrue("Signed image should be larger than 0", result.size > 0)
                    assertTrue("Signed image should be larger than original", result.size > sourceImageData.size)
                } finally {
                    signer?.close()
                }
            } finally {
                sourceStream.close()
                destStream.close()
            }
        } finally {
            builder?.close()
        }
    }
    
    @Test
    fun testMemoryStreamOperations() {
        val testData = "Hello, C2PA Stream!".toByteArray()
        val stream = MemoryC2PAStream(testData)
        
        try {
            // Test read
            val buffer = ByteArray(5)
            val bytesRead = stream.read(buffer, 5)
            assertEquals("Should read 5 bytes", 5, bytesRead)
            assertEquals("Should read correct data", "Hello", String(buffer))
            
            // Test seek
            val newPos = stream.seek(0, SeekMode.START.value)
            assertEquals("Should seek to start", 0, newPos)
            
            // Test write
            val writeData = "Test".toByteArray()
            val bytesWritten = stream.write(writeData, 4)
            assertEquals("Should write 4 bytes", 4, bytesWritten)
            
            // Test flush
            val flushResult = stream.flush()
            assertEquals("Flush should succeed", 0, flushResult)
        } finally {
            stream.close()
        }
    }
    
    private fun getResourceAsBytes(context: Context, resourceId: Int): ByteArray {
        val inputStream = context.resources.openRawResource(resourceId)
        val data = inputStream.readBytes()
        inputStream.close()
        return data
    }
    
    private fun getResourceAsString(context: Context, resourceId: Int): String {
        val inputStream = context.resources.openRawResource(resourceId)
        val text = inputStream.bufferedReader().use { it.readText() }
        inputStream.close()
        return text
    }
    
    private fun copyResourceToFile(context: Context, resourceId: Int, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        val inputStream = context.resources.openRawResource(resourceId)
        file.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        inputStream.close()
        return file
    }
}
