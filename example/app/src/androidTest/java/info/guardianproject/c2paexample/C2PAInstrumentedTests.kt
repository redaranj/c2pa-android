package info.guardianproject.c2paexample

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import info.guardianproject.c2pa.*
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Before
import java.io.File

@RunWith(AndroidJUnit4::class)
class C2PAUnitTests {
    
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
        val testImageFile = copyResourceToFile(R.raw.adobe_20220124_ci, "test_adobe.jpg")
        
        val manifest = C2PA.readFile(testImageFile.absolutePath)
        assertNotNull("Should read manifest from Adobe test image", manifest)
        
        val json = JSONObject(manifest!!)
        assertTrue("Should contain manifests", json.has("manifests"))
        
        testImageFile.delete()
    }
    
    @Test
    fun testStreamAPI() {
        val testImageData = getResourceAsBytes(R.raw.adobe_20220124_ci)
        val stream = MemoryC2PAStream(testImageData)
        
        val reader = C2PAReader.fromStream("image/jpeg", stream)
        assertNotNull("Should create reader from stream", reader)
        
        val json = reader!!.toJson()
        assertNotNull("Should get JSON from reader", json)
        assertTrue("JSON should not be empty", json.isNotEmpty())
        
        reader.close()
        stream.close()
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
        
        val sourceImageData = getResourceAsBytes(R.raw.pexels_asadphoto_457882)
        val sourceStream = MemoryC2PAStream(sourceImageData)
        val destStream = MemoryC2PAStream()
        
        val certPem = getResourceAsString(R.raw.es256_certs)
        val keyPem = getResourceAsString(R.raw.es256_private)
        
        val signerInfo = SignerInfo(
            alg = "es256",
            signCert = certPem,
            privateKey = keyPem
        )
        
        val signer = C2PASigner.fromInfo(signerInfo)
        assertNotNull("Should create signer", signer)
        
        val result = builder!!.sign("image/jpeg", sourceStream, destStream, signer!!)
        assertTrue("Signed image should be larger than 0", result.size > 0)
        assertTrue("Signed image should be larger than original", result.size > sourceImageData.size)
        
        signer.close()
        builder.close()
        sourceStream.close()
        destStream.close()
    }
    
    @Test
    fun testBuilderNoEmbed() {
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
        assertNotNull("Should create builder", builder)
        
        builder!!.setNoEmbed()
        
        val archiveStream = MemoryC2PAStream()
        val result = builder.toArchive(archiveStream)
        assertEquals("Should successfully create archive", 0, result)
        
        val archiveData = archiveStream.getData()
        assertTrue("Archive should contain data", archiveData.isNotEmpty())
        
        builder.close()
        archiveStream.close()
    }
    
    @Test
    fun testReadIngredient() {
        val testImageFile = copyResourceToFile(R.raw.adobe_20220124_ci, "test_ingredient.jpg")
        
        val ingredient = C2PA.readIngredientFile(testImageFile.absolutePath)
        
        testImageFile.delete()
    }
    
    @Test
    fun testInvalidFileHandling() {
        val textFile = File(context.cacheDir, "test.txt")
        textFile.writeText("This is not an image file")
        
        val result = C2PA.readFile(textFile.absolutePath)
        assertNull("Should return null for text file", result)
        
        val error = C2PA.getError()
        assertNotNull("Should have error for invalid file", error)
        
        textFile.delete()
    }
    
    @Test
    fun testResourceReading() {
        val testImageData = getResourceAsBytes(R.raw.adobe_20220124_ci)
        val stream = MemoryC2PAStream(testImageData)
        
        val reader = C2PAReader.fromStream("image/jpeg", stream)
        if (reader != null) {
            val resourceStream = MemoryC2PAStream()
            reader.resourceToStream("thumbnail", resourceStream)
            reader.close()
        }
        
        stream.close()
    }
    
    @Test
    fun testBuilderRemoteURL() {
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
        assertNotNull("Should create builder", builder)
        
        val result = builder!!.setRemoteUrl("https://example.com/manifest.c2pa")
        assertEquals("Should set remote URL successfully", 0, result)
        
        builder.close()
    }
    
    @Test
    fun testBuilderAddResource() {
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
        assertNotNull("Should create builder", builder)
        
        val thumbnailData = createSimpleJPEGThumbnail()
        val thumbnailStream = MemoryC2PAStream(thumbnailData)
        
        val result = builder!!.addResource("thumbnail", thumbnailStream)
        assertEquals("Should add resource successfully", 0, result)
        
        builder.close()
        thumbnailStream.close()
    }
    
    @Test
    fun testBuilderAddIngredient() {
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
        assertNotNull("Should create builder", builder)
        
        val ingredientJson = """{
            "title": "Test Ingredient",
            "format": "image/jpeg"
        }"""
        
        val ingredientImageData = getResourceAsBytes(R.raw.pexels_asadphoto_457882)
        val ingredientStream = MemoryC2PAStream(ingredientImageData)
        
        val result = builder!!.addIngredientFromStream(ingredientJson, "image/jpeg", ingredientStream)
        assertEquals("Should add ingredient successfully", 0, result)
        
        builder.close()
        ingredientStream.close()
    }
    
    @Test
    fun testBuilderFromArchive() {
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
        
        val originalBuilder = C2PABuilder.fromJson(manifestJson)
        assertNotNull("Should create original builder", originalBuilder)
        
        originalBuilder!!.setNoEmbed()
        val archiveStream = MemoryC2PAStream()
        originalBuilder.toArchive(archiveStream)
        originalBuilder.close()
        
        archiveStream.seek(0, SeekMode.START.value)
        val newBuilder = C2PABuilder.fromArchive(archiveStream)
        assertNotNull("Should create builder from archive", newBuilder)
        
        newBuilder?.close()
        archiveStream.close()
    }
    
    @Test
    fun testReaderWithManifestData() {
        val manifestData = ByteArray(1024) { it.toByte() }
        val imageData = getResourceAsBytes(R.raw.pexels_asadphoto_457882)
        val stream = MemoryC2PAStream(imageData)
        
        val reader = C2PAReader.fromManifestDataAndStream("image/jpeg", stream, manifestData)
        
        reader?.close()
        stream.close()
    }
    
    @Test
    fun testSignerWithCallback() {
        val certPem = getResourceAsString(R.raw.es256_certs)
        
        var callbackInvoked = false
        val callback = object : SignCallback {
            override fun sign(data: ByteArray): ByteArray? {
                callbackInvoked = true
                return ByteArray(64) { 0x42 }
            }
        }
        
        val signer = C2PASigner.fromCallback("es256", certPem, null, callback)
        assertNotNull("Should create callback signer", signer)
        
        val reserveSize = signer!!.reserveSize()
        assertTrue("Reserve size should be positive", reserveSize > 0)
        
        signer.close()
    }
    
    @Test
    fun testFileOperationsWithDataDir() {
        val testImageFile = copyResourceToFile(R.raw.adobe_20220124_ci, "test_datadir.jpg")
        val dataDir = File(context.cacheDir, "c2pa_data")
        dataDir.mkdirs()
        
        val manifest = C2PA.readFile(testImageFile.absolutePath, dataDir.absolutePath)
        val ingredient = C2PA.readIngredientFile(testImageFile.absolutePath, dataDir.absolutePath)
        
        testImageFile.delete()
        dataDir.deleteRecursively()
    }
    
    @Test
    fun testWriteOnlyStreams() {
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
        assertNotNull("Should create builder", builder)
        
        builder!!.setNoEmbed()
        
        val writeOnlyStream = MemoryC2PAStream()
        val result = builder.toArchive(writeOnlyStream)
        assertEquals("Should write to write-only stream", 0, result)
        
        val data = writeOnlyStream.getData()
        assertTrue("Should have written data", data.isNotEmpty())
        
        builder.close()
        writeOnlyStream.close()
    }
    
    @Test
    fun testCustomStreamCallbacks() {
        var readCalled = false
        var writeCalled = false
        var seekCalled = false
        var flushCalled = false
        
        val customStream = object : MemoryC2PAStream() {
            override fun read(buffer: ByteArray, length: Long): Long {
                readCalled = true
                return super.read(buffer, length)
            }
            
            override fun write(data: ByteArray, length: Long): Long {
                writeCalled = true
                return super.write(data, length)
            }
            
            override fun seek(offset: Long, mode: Int): Long {
                seekCalled = true
                return super.seek(offset, mode)
            }
            
            override fun flush(): Long {
                flushCalled = true
                return super.flush()
            }
        }
        
        customStream.write(ByteArray(10), 10)
        customStream.seek(0, SeekMode.START.value)
        customStream.read(ByteArray(5), 5)
        customStream.flush()
        
        assertTrue("Read should be called", readCalled)
        assertTrue("Write should be called", writeCalled)
        assertTrue("Seek should be called", seekCalled)
        assertTrue("Flush should be called", flushCalled)
        
        customStream.close()
    }
    
    @Test
    fun testStreamFileOptions() {
        val tempFile = File.createTempFile("stream_test", ".dat", context.cacheDir)
        tempFile.writeBytes(ByteArray(100) { it.toByte() })
        
        val preserveStream = FileC2PAStream(java.io.RandomAccessFile(tempFile, "r"))
        val buffer = ByteArray(50)
        val bytesRead = preserveStream.read(buffer, 50)
        assertEquals("Should read data from existing file", 50, bytesRead)
        preserveStream.close()
        
        val truncateStream = FileC2PAStream(java.io.RandomAccessFile(tempFile, "rw"))
        truncateStream.write(ByteArray(10) { 0xFF.toByte() }, 10)
        truncateStream.close()
        
        tempFile.delete()
    }
    
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
    
    private fun createSimpleJPEGThumbnail(): ByteArray {
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46,
            0x00, 0x01,
            0x01, 0x01,
            0x00, 0x48,
            0x00, 0x48,
            0x00, 0x00,
            0xFF.toByte(), 0xD9.toByte()
        )
    }
}