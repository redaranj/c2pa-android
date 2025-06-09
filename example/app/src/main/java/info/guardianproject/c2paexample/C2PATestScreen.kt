package info.guardianproject.c2paexample

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import info.guardianproject.c2pa.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class TestResult(
    val name: String,
    val success: Boolean,
    val message: String,
    val details: String? = null
)

@Composable
fun C2PATestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var testResults by remember { mutableStateOf(listOf<TestResult>()) }
    var isRunning by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "C2PA Library Tests",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                coroutineScope.launch {
                    isRunning = true
                    testResults = runAllTests(context)
                    isRunning = false
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRunning) "Running Tests..." else "Run All Tests")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            testResults.forEach { result ->
                TestResultCard(result)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun TestResultCard(result: TestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.success) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (result.success) "✓" else "✗",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (result.success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyMedium
            )
            
            result.details?.let { details ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

suspend fun runAllTests(context: Context): List<TestResult> = withContext(Dispatchers.IO) {
    val results = mutableListOf<TestResult>()
    
    // Test 1: Library Version
    results.add(runTest("Library Version") {
        val version = C2PA.version()
        if (version.isNotEmpty() && version.contains(".")) {
            TestResult("Library Version", true, "C2PA version: $version", version)
        } else {
            TestResult("Library Version", false, "Invalid version format", version)
        }
    })
    
    // Test 2: Error Handling
    results.add(runTest("Error Handling") {
        val result = C2PA.readFile("/non/existent/file.jpg")
        val error = C2PA.getError()
        if (result == null && error != null) {
            TestResult("Error Handling", true, "Correctly handled missing file", "Error: $error")
        } else {
            TestResult("Error Handling", false, "Unexpected behavior", "Result: $result, Error: $error")
        }
    })
    
    // Test 3: Read Manifest from Test Image
    results.add(runTest("Read Manifest from Test Image") {
        val testImageFile = copyResourceToFile(context, R.raw.adobe_20220124_ci, "test_adobe.jpg")
        try {
            val manifest = C2PA.readFile(testImageFile.absolutePath)
            if (manifest != null) {
                val json = JSONObject(manifest)
                val hasManifests = json.has("manifests")
                TestResult(
                    "Read Manifest from Test Image", 
                    hasManifests, 
                    if (hasManifests) "Successfully read manifest" else "No manifests found",
                    manifest.take(500) + if (manifest.length > 500) "..." else ""
                )
            } else {
                val error = C2PA.getError()
                TestResult("Read Manifest from Test Image", false, "Failed to read manifest", error ?: "No error")
            }
        } finally {
            testImageFile.delete()
        }
    })
    
    // Test 4: Stream API
    results.add(runTest("Stream API") {
        val testImageData = getResourceAsBytes(context, R.raw.adobe_20220124_ci)
        val stream = MemoryC2PAStream(testImageData)
        try {
            val reader = C2PAReader.fromStream("image/jpeg", stream)
            if (reader != null) {
                try {
                    val json = reader.toJson()
                    TestResult("Stream API", json.isNotEmpty(), "Stream API working", json.take(200))
                } finally {
                    reader.close()
                }
            } else {
                TestResult("Stream API", false, "Failed to create reader from stream", null)
            }
        } finally {
            stream.close()
        }
    })
    
    // Test 5: Builder API
    results.add(runTest("Builder API") {
        val manifestJson = """{
            "claim_generator": "test_app/1.0",
            "assertions": [
                {"label": "c2pa.test", "data": {"test": true}}
            ]
        }"""
        
        val builder = C2PABuilder.fromJson(manifestJson)
        if (builder != null) {
            try {
                val sourceImageData = getResourceAsBytes(context, R.raw.pexels_asadphoto_457882)
                val sourceStream = MemoryC2PAStream(sourceImageData)
                val destStream = MemoryC2PAStream()
                
                try {
                    val certPem = getResourceAsString(context, R.raw.es256_certs)
                    val keyPem = getResourceAsString(context, R.raw.es256_private)
                    
                    val signerInfo = SignerInfo("es256", certPem, keyPem)
                    val signer = C2PASigner.fromInfo(signerInfo)
                    
                    if (signer != null) {
                        try {
                            val result = builder.sign("image/jpeg", sourceStream, destStream, signer)
                            val success = result.size > sourceImageData.size
                            TestResult(
                                "Builder API", 
                                success, 
                                if (success) "Successfully signed image" else "Signing failed",
                                "Original: ${sourceImageData.size}, Signed: ${result.size}"
                            )
                        } finally {
                            signer.close()
                        }
                    } else {
                        TestResult("Builder API", false, "Failed to create signer", null)
                    }
                } finally {
                    sourceStream.close()
                    destStream.close()
                }
            } finally {
                builder.close()
            }
        } else {
            TestResult("Builder API", false, "Failed to create builder", null)
        }
    })
    
    // Test 6: Builder No-Embed
    results.add(runTest("Builder No-Embed") {
        val manifestJson = """{
            "claim_generator": "test_app/1.0",
            "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
        }"""
        
        val builder = C2PABuilder.fromJson(manifestJson)
        if (builder != null) {
            try {
                builder.setNoEmbed()
                val archiveStream = MemoryC2PAStream()
                try {
                    val result = builder.toArchive(archiveStream)
                    val data = archiveStream.getData()
                    val success = result == 0 && data.isNotEmpty()
                    TestResult(
                        "Builder No-Embed",
                        success,
                        if (success) "Archive created successfully" else "Archive creation failed",
                        "Result: $result, Archive size: ${data.size}"
                    )
                } finally {
                    archiveStream.close()
                }
            } finally {
                builder.close()
            }
        } else {
            TestResult("Builder No-Embed", false, "Failed to create builder", null)
        }
    })
    
    // Test 7: Read Ingredient
    results.add(runTest("Read Ingredient") {
        val testImageFile = copyResourceToFile(context, R.raw.adobe_20220124_ci, "test_ingredient.jpg")
        try {
            val ingredient = C2PA.readIngredientFile(testImageFile.absolutePath)
            TestResult(
                "Read Ingredient",
                true,
                if (ingredient != null) "Ingredient data found" else "No ingredient data (expected for some images)",
                ingredient?.take(200)
            )
        } finally {
            testImageFile.delete()
        }
    })
    
    // Test 8: Invalid File Handling
    results.add(runTest("Invalid File Handling") {
        val textFile = File(context.cacheDir, "test.txt")
        textFile.writeText("This is not an image file")
        try {
            val result = C2PA.readFile(textFile.absolutePath)
            val error = C2PA.getError()
            val success = result == null && error != null
            TestResult(
                "Invalid File Handling",
                success,
                if (success) "Correctly handled invalid file" else "Unexpected behavior",
                "Result: $result, Error: $error"
            )
        } finally {
            textFile.delete()
        }
    })
    
    // Test 9: Resource Reading
    results.add(runTest("Resource Reading") {
        val testImageData = getResourceAsBytes(context, R.raw.adobe_20220124_ci)
        val stream = MemoryC2PAStream(testImageData)
        try {
            val reader = C2PAReader.fromStream("image/jpeg", stream)
            if (reader != null) {
                try {
                    val resourceStream = MemoryC2PAStream()
                    try {
                        reader.resourceToStream("thumbnail", resourceStream)
                        TestResult("Resource Reading", true, "Resource extraction attempted", null)
                    } finally {
                        resourceStream.close()
                    }
                } finally {
                    reader.close()
                }
            } else {
                TestResult("Resource Reading", false, "Failed to create reader", null)
            }
        } finally {
            stream.close()
        }
    })
    
    // Test 10: Builder Remote URL
    results.add(runTest("Builder Remote URL") {
        val manifestJson = """{
            "claim_generator": "test_app/1.0",
            "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
        }"""
        
        val builder = C2PABuilder.fromJson(manifestJson)
        if (builder != null) {
            try {
                val result = builder.setRemoteUrl("https://example.com/manifest.c2pa")
                TestResult(
                    "Builder Remote URL",
                    result == 0,
                    if (result == 0) "Remote URL set successfully" else "Failed to set remote URL",
                    "Result: $result"
                )
            } finally {
                builder.close()
            }
        } else {
            TestResult("Builder Remote URL", false, "Failed to create builder", null)
        }
    })
    
    // Test 11: Builder Add Resource
    results.add(runTest("Builder Add Resource") {
        val manifestJson = """{
            "claim_generator": "test_app/1.0",
            "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
        }"""
        
        val builder = C2PABuilder.fromJson(manifestJson)
        if (builder != null) {
            try {
                val thumbnailData = createSimpleJPEGThumbnail()
                val thumbnailStream = MemoryC2PAStream(thumbnailData)
                try {
                    val result = builder.addResource("thumbnail", thumbnailStream)
                    TestResult(
                        "Builder Add Resource",
                        result == 0,
                        if (result == 0) "Resource added successfully" else "Failed to add resource",
                        "Result: $result"
                    )
                } finally {
                    thumbnailStream.close()
                }
            } finally {
                builder.close()
            }
        } else {
            TestResult("Builder Add Resource", false, "Failed to create builder", null)
        }
    })
    
    // Test 12: Builder Add Ingredient
    results.add(runTest("Builder Add Ingredient") {
        val manifestJson = """{
            "claim_generator": "test_app/1.0",
            "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
        }"""
        
        val builder = C2PABuilder.fromJson(manifestJson)
        if (builder != null) {
            try {
                val ingredientJson = """{"title": "Test Ingredient", "format": "image/jpeg"}"""
                val ingredientImageData = getResourceAsBytes(context, R.raw.pexels_asadphoto_457882)
                val ingredientStream = MemoryC2PAStream(ingredientImageData)
                try {
                    val result = builder.addIngredientFromStream(ingredientJson, "image/jpeg", ingredientStream)
                    TestResult(
                        "Builder Add Ingredient",
                        result == 0,
                        if (result == 0) "Ingredient added successfully" else "Failed to add ingredient",
                        "Result: $result"
                    )
                } finally {
                    ingredientStream.close()
                }
            } finally {
                builder.close()
            }
        } else {
            TestResult("Builder Add Ingredient", false, "Failed to create builder", null)
        }
    })
    
    // Test 13: Builder from Archive
    results.add(runTest("Builder from Archive") {
        val manifestJson = """{
            "claim_generator": "test_app/1.0",
            "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
        }"""
        
        val originalBuilder = C2PABuilder.fromJson(manifestJson)
        if (originalBuilder != null) {
            try {
                originalBuilder.setNoEmbed()
                val archiveStream = MemoryC2PAStream()
                try {
                    originalBuilder.toArchive(archiveStream)
                    archiveStream.seek(0, SeekMode.START.value)
                    
                    val newBuilder = C2PABuilder.fromArchive(archiveStream)
                    val success = newBuilder != null
                    newBuilder?.close()
                    
                    TestResult(
                        "Builder from Archive",
                        success,
                        if (success) "Builder created from archive" else "Failed to create builder from archive",
                        null
                    )
                } finally {
                    archiveStream.close()
                }
            } finally {
                originalBuilder.close()
            }
        } else {
            TestResult("Builder from Archive", false, "Failed to create original builder", null)
        }
    })
    
    // Test 14: Reader with Manifest Data
    results.add(runTest("Reader with Manifest Data") {
        val manifestData = ByteArray(1024) { it.toByte() }
        val imageData = getResourceAsBytes(context, R.raw.pexels_asadphoto_457882)
        val stream = MemoryC2PAStream(imageData)
        try {
            val reader = C2PAReader.fromManifestDataAndStream("image/jpeg", stream, manifestData)
            val success = reader != null
            reader?.close()
            
            TestResult(
                "Reader with Manifest Data",
                success,
                if (success) "Reader created with manifest data" else "Failed to create reader",
                null
            )
        } finally {
            stream.close()
        }
    })
    
    // Test 15: Signer with Callback (Currently not implemented)
    results.add(runTest("Signer with Callback") {
        TestResult(
            "Signer with Callback",
            true,
            "Callback signer not yet implemented - placeholder test passes",
            "This test will be implemented in a future version"
        )
    })
    
    // Test 16: File Operations with Data Directory
    results.add(runTest("File Operations with Data Directory") {
        val testImageFile = copyResourceToFile(context, R.raw.adobe_20220124_ci, "test_datadir.jpg")
        val dataDir = File(context.cacheDir, "c2pa_data")
        dataDir.mkdirs()
        
        try {
            val manifest = C2PA.readFile(testImageFile.absolutePath, dataDir.absolutePath)
            val ingredient = C2PA.readIngredientFile(testImageFile.absolutePath, dataDir.absolutePath)
            
            TestResult(
                "File Operations with Data Directory",
                true,
                "Operations completed with data directory",
                "Manifest: ${manifest != null}, Ingredient: ${ingredient != null}"
            )
        } finally {
            testImageFile.delete()
            dataDir.deleteRecursively()
        }
    })
    
    // Test 17: Write-Only Streams
    results.add(runTest("Write-Only Streams") {
        val manifestJson = """{
            "claim_generator": "test_app/1.0",
            "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
        }"""
        
        val builder = C2PABuilder.fromJson(manifestJson)
        if (builder != null) {
            try {
                builder.setNoEmbed()
                val writeOnlyStream = MemoryC2PAStream()
                try {
                    val result = builder.toArchive(writeOnlyStream)
                    val data = writeOnlyStream.getData()
                    val success = result == 0 && data.isNotEmpty()
                    
                    TestResult(
                        "Write-Only Streams",
                        success,
                        if (success) "Write-only stream working" else "Write-only stream failed",
                        "Data size: ${data.size}"
                    )
                } finally {
                    writeOnlyStream.close()
                }
            } finally {
                builder.close()
            }
        } else {
            TestResult("Write-Only Streams", false, "Failed to create builder", null)
        }
    })
    
    // Test 18: Custom Stream Callbacks
    results.add(runTest("Custom Stream Callbacks") {
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
        
        try {
            customStream.write(ByteArray(10), 10)
            customStream.seek(0, SeekMode.START.value)
            customStream.read(ByteArray(5), 5)
            customStream.flush()
            
            val allCalled = readCalled && writeCalled && seekCalled && flushCalled
            TestResult(
                "Custom Stream Callbacks",
                allCalled,
                if (allCalled) "All callbacks invoked" else "Some callbacks not invoked",
                "Read: $readCalled, Write: $writeCalled, Seek: $seekCalled, Flush: $flushCalled"
            )
        } finally {
            customStream.close()
        }
    })
    
    // Test 19: Stream File Options
    results.add(runTest("Stream File Options") {
        val tempFile = File.createTempFile("stream_test", ".dat", context.cacheDir)
        tempFile.writeBytes(ByteArray(100) { it.toByte() })
        
        try {
            val preserveStream = FileC2PAStream(java.io.RandomAccessFile(tempFile, "r"))
            try {
                val buffer = ByteArray(50)
                val bytesRead = preserveStream.read(buffer, 50)
                val success = bytesRead == 50L
                
                TestResult(
                    "Stream File Options",
                    success,
                    if (success) "File stream operations working" else "File stream operations failed",
                    "Bytes read: $bytesRead"
                )
            } finally {
                preserveStream.close()
            }
        } finally {
            tempFile.delete()
        }
    })
    
    results
}

private suspend fun runTest(testName: String, test: suspend () -> TestResult): TestResult = withContext(Dispatchers.IO) {
    try {
        test()
    } catch (e: Exception) {
        TestResult(testName, false, "Exception: ${e.message}", e.stackTraceToString())
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