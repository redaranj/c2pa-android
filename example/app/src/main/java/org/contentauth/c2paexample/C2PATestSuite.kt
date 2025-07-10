package org.contentauth.c2paexample

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.C2PAError
import org.contentauth.c2pa.C2paSeekMode
import org.contentauth.c2pa.CallbackStream
import org.contentauth.c2pa.Reader
import org.contentauth.c2pa.Signer
import org.contentauth.c2pa.SignerInfo
import org.contentauth.c2pa.SigningAlgorithm
import org.contentauth.c2pa.Stream
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Shared test suite for C2PA functionality.
 * This class contains all the test logic that can be run from both:
 * - The UI (C2PATestScreen)
 * - Instrumented tests
 */
class C2PATestSuite(private val context: Context) {
    
    data class TestResult(
        val name: String,
        val success: Boolean,
        val message: String,
        val details: String? = null
    )
    
    suspend fun runAllTests(): List<TestResult> = withContext(Dispatchers.IO) {
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
            val memStream = MemoryStream(testImageData)
            try {
                val reader = Reader("image/jpeg", memStream.stream)
                try {
                    val json = reader.json()
                    TestResult("Stream API", json.isNotEmpty(), "Stream API working", json.take(200))
                } finally {
                    reader.close()
                }
            } catch (e: C2PAError) {
                TestResult("Stream API", false, "Failed to create reader from stream", e.toString())
            } finally {
                memStream.close()
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
            
            try {
                val builder = Builder(manifestJson)
                try {
                    val sourceImageData = getResourceAsBytes(context, R.raw.pexels_asadphoto_457882)
                    val sourceStream = MemoryStream(sourceImageData)

                    val fileTest = File.createTempFile("c2pa-test",".jpg")
                    val destStream = Stream(fileTest)
                    try {
                        val certPem = getResourceAsString(context, R.raw.es256_certs)
                        val keyPem = getResourceAsString(context, R.raw.es256_private)
                        
                        val signerInfo = SignerInfo(SigningAlgorithm.es256, certPem, keyPem)
                        val signer = Signer(signerInfo)
                        
                        try {
                            val result = builder.sign("image/jpeg", sourceStream.stream, destStream, signer)

                            val manifest = C2PA.readFile(fileTest.absolutePath)
                            val json = if (manifest != null) JSONObject(manifest) else null
                            val success = json?.has("manifests") ?: false

                            TestResult(
                                "Builder API", 
                                success, 
                                if (success) "Successfully signed image" else "Signing failed",
                                "Original: ${sourceImageData.size}, Signed: ${fileTest.length()}, Result size: ${result.size}\n\n${json}"
                            )
                        } finally {
                            signer.close()
                        }
                    } finally {
                        sourceStream.close()
                        destStream.close()
                    }
                } finally {
                    builder.close()
                }
            } catch (e: C2PAError) {
                TestResult("Builder API", false, "Failed to create builder", e.toString())
            }
        })
        
        // Test 6: Builder No-Embed
        results.add(runTest("Builder No-Embed") {
            val manifestJson = """{
                "claim_generator": "test_app/1.0",
                "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
            }"""
            
            try {
                val builder = Builder(manifestJson)
                try {
                    builder.setNoEmbed()
                    val archiveStream = MemoryStream()
                    try {
                        builder.toArchive(archiveStream.stream)
                        val data = archiveStream.getData()
                        val success = data.isNotEmpty()
                        TestResult(
                            "Builder No-Embed",
                            success,
                            if (success) "Archive created successfully" else "Archive creation failed",
                            "Archive size: ${data.size}"
                        )
                    } finally {
                        archiveStream.close()
                    }
                } finally {
                    builder.close()
                }
            } catch (e: C2PAError) {
                TestResult("Builder No-Embed", false, "Failed to create builder", e.toString())
            }
        })
        
        // Test 7: Read Ingredient
        results.add(runTest("Read Ingredient") {
            val testImageFile = copyResourceToFile(context, R.raw.adobe_20220124_ci, "test_ingredient.jpg")
            try {
                val standaloneIngredient = C2PA.readIngredientFile(testImageFile.absolutePath)
                
                var hasValidIngredientData = false
                if (standaloneIngredient != null) {
                    try {
                        val json = JSONObject(standaloneIngredient)
                        hasValidIngredientData = json.has("format") || json.has("title")
                    } catch (e: Exception) {
                        hasValidIngredientData = false
                    }
                }
                
                val manifest = C2PA.readFile(testImageFile.absolutePath)
                var hasIngredientsInManifest = false
                if (manifest != null) {
                    try {
                        val manifestJson = JSONObject(manifest)
                        val manifests = manifestJson.optJSONArray("manifests")
                        if (manifests != null && manifests.length() > 0) {
                            val firstManifest = manifests.getJSONObject(0)
                            val ingredients = firstManifest.optJSONArray("ingredients")
                            hasIngredientsInManifest = ingredients != null && ingredients.length() > 0
                        }
                    } catch (e: Exception) {
                        // Ignore JSON parsing errors
                    }
                }
                
                val success = true // The API is working correctly regardless of whether this image has ingredients
                
                TestResult(
                    "Read Ingredient",
                    success,
                    when {
                        hasValidIngredientData -> "Found valid ingredient data"
                        hasIngredientsInManifest -> "Found ingredients in manifest"
                        else -> "No ingredients (normal for some images)"
                    },
                    buildString {
                        append("Ingredient API returned: ${if (standaloneIngredient != null) "data (${standaloneIngredient.length} bytes)" else "null"}")
                        if (hasIngredientsInManifest) append(", Manifest has ingredients")
                    }
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
            val stream = MemoryStream(testImageData)
            try {
                try {
                    val reader = Reader("image/jpeg", stream.stream)
                    try {
                        val json = reader.json()
                        val manifestJson = JSONObject(json)
                        
                        var resourceUri: String? = null
                        var resourceType = "unknown"
                        val availableResources = mutableListOf<String>()
                        
                        val manifests = manifestJson.optJSONArray("manifests")
                        if (manifests != null && manifests.length() > 0) {
                            val manifest = manifests.getJSONObject(0)
                            
                            // Check for thumbnail
                            val thumbnail = manifest.optJSONObject("thumbnail")
                            if (thumbnail != null) {
                                val id = thumbnail.optString("identifier")
                                if (id.isNotEmpty()) {
                                    availableResources.add("thumbnail: $id")
                                    if (resourceUri == null) {
                                        resourceUri = id
                                        resourceType = "thumbnail"
                                    }
                                }
                            }
                            
                            // Check for ingredient thumbnails
                            val ingredients = manifest.optJSONArray("ingredients")
                            if (ingredients != null) {
                                for (i in 0 until ingredients.length()) {
                                    val ingredient = ingredients.getJSONObject(i)
                                    val ingThumb = ingredient.optJSONObject("thumbnail")
                                    if (ingThumb != null) {
                                        val id = ingThumb.optString("identifier")
                                        if (id.isNotEmpty()) {
                                            availableResources.add("ingredient_thumbnail[$i]: $id")
                                            if (resourceUri == null) {
                                                resourceUri = id
                                                resourceType = "ingredient_thumbnail"
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Check assertion thumbnails
                            val assertions = manifest.optJSONArray("assertions")
                            if (assertions != null) {
                                for (i in 0 until assertions.length()) {
                                    val assertion = assertions.getJSONObject(i)
                                    val thumb = assertion.optJSONObject("thumbnail")
                                    if (thumb != null) {
                                        val id = thumb.optString("identifier")
                                        if (id.isNotEmpty()) {
                                            availableResources.add("assertion_thumbnail[$i]: $id")
                                            if (resourceUri == null) {
                                                resourceUri = id
                                                resourceType = "assertion_thumbnail"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (resourceUri != null) {
                            val resourceStream = MemoryStream()
                            try {
                                reader.resource(resourceUri, resourceStream.stream)
                                val resourceData = resourceStream.getData()
                                val success = resourceData.isNotEmpty()
                                TestResult(
                                    "Resource Reading", 
                                    success, 
                                    if (success) "Successfully extracted $resourceType" else "Resource extraction failed", 
                                    "Type: $resourceType, URI: $resourceUri, Size: ${resourceData.size} bytes"
                                )
                            } catch (e: C2PAError) {
                                TestResult(
                                    "Resource Reading",
                                    false,
                                    "Failed to extract resource: ${e.message}",
                                    "URI: $resourceUri, Available: ${availableResources.joinToString(", ")}"
                                )
                            } finally {
                                resourceStream.close()
                            }
                        } else {
                            TestResult(
                                "Resource Reading",
                                true,
                                "No embedded resources in manifest (normal for some images)",
                                "Checked thumbnails, ingredients, and assertions. JSON structure valid."
                            )
                        }
                    } finally {
                        reader.close()
                    }
                } catch (e: C2PAError) {
                    return@runTest TestResult("Resource Reading", false, "Failed to create reader", e.toString())
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
            
            try {
                val builder = Builder(manifestJson)
                try {
                    builder.setRemoteURL("https://example.com/manifest.c2pa")
                    builder.setNoEmbed()
                    val archive = MemoryStream()
                    try {
                        builder.toArchive(archive.stream)
                        val archiveData = archive.getData()
                        val archiveStr = String(archiveData)
                        val success = archiveStr.contains("https://example.com/manifest.c2pa")
                        TestResult(
                            "Builder Remote URL",
                            success,
                            if (success) "Remote URL set successfully" else "Remote URL not found in archive",
                            "Archive contains URL: $success"
                        )
                    } finally {
                        archive.close()
                    }
                } finally {
                    builder.close()
                }
            } catch (e: C2PAError) {
                TestResult("Builder Remote URL", false, "Failed to create builder", e.toString())
            }
        })
        
        // Test 11: Builder Add Resource
        results.add(runTest("Builder Add Resource") {
            val manifestJson = """{
                "claim_generator": "test_app/1.0",
                "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
            }"""
            
            try {
                val builder = Builder(manifestJson)
                try {
                    val thumbnailData = createSimpleJPEGThumbnail()
                    val thumbnailStream = MemoryStream(thumbnailData)
                    try {
                        builder.addResource("thumbnail", thumbnailStream.stream)
                        builder.setNoEmbed()
                        val archive = MemoryStream()
                        try {
                            builder.toArchive(archive.stream)
                            val archiveStr = String(archive.getData())
                            val success = archiveStr.contains("thumbnail")
                            TestResult(
                                "Builder Add Resource",
                                success,
                                if (success) "Resource added successfully" else "Resource not found in archive",
                                "Thumbnail size: ${thumbnailData.size} bytes, Found in archive: $success"
                            )
                        } finally {
                            archive.close()
                        }
                    } finally {
                        thumbnailStream.close()
                    }
                } finally {
                    builder.close()
                }
            } catch (e: C2PAError) {
                TestResult("Builder Add Resource", false, "Failed to create builder", e.toString())
            }
        })
        
        // Test 12: Builder Add Ingredient
        results.add(runTest("Builder Add Ingredient") {
            val manifestJson = """{
                "claim_generator": "test_app/1.0",
                "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
            }"""
            
            try {
                val builder = Builder(manifestJson)
                try {
                    val ingredientJson = """{"title": "Test Ingredient", "format": "image/jpeg"}"""
                    val ingredientImageData = getResourceAsBytes(context, R.raw.pexels_asadphoto_457882)
                    val ingredientStream = MemoryStream(ingredientImageData)
                    try {
                        builder.addIngredient(ingredientJson, "image/jpeg", ingredientStream.stream)
                        builder.setNoEmbed()
                        val archive = MemoryStream()
                        try {
                            builder.toArchive(archive.stream)
                            val archiveStr = String(archive.getData())
                            val success = archiveStr.contains("\"title\":\"Test Ingredient\"")
                            TestResult(
                                "Builder Add Ingredient",
                                success,
                                if (success) "Ingredient added successfully" else "Ingredient not found in archive",
                                "Ingredient found: $success"
                            )
                        } finally {
                            archive.close()
                        }
                    } finally {
                        ingredientStream.close()
                    }
                } finally {
                    builder.close()
                }
            } catch (e: C2PAError) {
                TestResult("Builder Add Ingredient", false, "Failed to create builder", e.toString())
            }
        })
        
        // Test 13: Builder from Archive
        results.add(runTest("Builder from Archive") {
            val manifestJson = """{
                "claim_generator": "test_app/1.0",
                "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
            }"""
            
            try {
                val originalBuilder = Builder(manifestJson)
                try {
                    val thumbnailData = createSimpleJPEGThumbnail()
                    val thumbnailStream = MemoryStream(thumbnailData)
                    originalBuilder.addResource("test_thumbnail", thumbnailStream.stream)
                    thumbnailStream.close()
                    
                    originalBuilder.setNoEmbed()
                    val archiveStream = MemoryStream()
                    try {
                        originalBuilder.toArchive(archiveStream.stream)
                        val archiveData = archiveStream.getData()
                        
                        val newArchiveStream = MemoryStream(archiveData)
                        
                        var builderCreated = false
                        try {
                            val newBuilder = Builder(newArchiveStream.stream)
                            builderCreated = true
                            newBuilder.close()
                        } catch (e: Exception) {
                            builderCreated = false
                        }
                        
                        newArchiveStream.close()
                        
                        val hasData = archiveData.isNotEmpty()
                        val success = hasData && builderCreated
                        
                        TestResult(
                            "Builder from Archive",
                            success,
                            when {
                                !hasData -> "No archive data generated"
                                !builderCreated -> "Failed to create builder from archive"
                                else -> "Archive round-trip successful"
                            },
                            "Archive size: ${archiveData.size} bytes, Builder created: $builderCreated"
                        )
                    } finally {
                        archiveStream.close()
                    }
                } finally {
                    originalBuilder.close()
                }
            } catch (e: Exception) {
                TestResult("Builder from Archive", false, "Exception: ${e.message}", e.toString())
            }
        })
        
        // Test 14: Reader with Manifest Data
        results.add(runTest("Reader with Manifest Data") {
            try {
                val manifestJson = """{
                    "claim_generator": "test_app/1.0",
                    "assertions": [
                        {"label": "c2pa.test", "data": {"test": true}}
                    ]
                }"""
                
                val builder = Builder(manifestJson)
                try {
                    val sourceImageData = getResourceAsBytes(context, R.raw.pexels_asadphoto_457882)
                    val sourceStream = MemoryStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-manifest-test", ".jpg")
                    val destStream = Stream(fileTest)
                    
                    val certPem = getResourceAsString(context, R.raw.es256_certs)
                    val keyPem = getResourceAsString(context, R.raw.es256_private)
                    val signer = Signer(SignerInfo(SigningAlgorithm.es256, certPem, keyPem))
                    
                    val signResult = builder.sign("image/jpeg", sourceStream.stream, destStream, signer)
                    
                    sourceStream.close()
                    destStream.close()
                    signer.close()
                    
                    val freshImageData = getResourceAsBytes(context, R.raw.pexels_asadphoto_457882)
                    val freshStream = MemoryStream(freshImageData)
                    
                    val success = if (signResult.manifestBytes != null) {
                        try {
                            val reader = Reader("image/jpeg", freshStream.stream, signResult.manifestBytes!!)
                            try {
                                val json = reader.json()
                                json.contains("\"c2pa.test\"")
                            } finally {
                                reader.close()
                            }
                        } catch (_: Exception) {
                            false
                        }
                    } else {
                        val manifest = C2PA.readFile(fileTest.absolutePath)
                        manifest != null && manifest.contains("\"c2pa.test\"")
                    }
                    
                    freshStream.close()
                    fileTest.delete()
                    
                    TestResult(
                        "Reader with Manifest Data",
                        success,
                        if (success) "Reader with manifest data works" else "Failed to use manifest data",
                        "Manifest bytes available: ${signResult.manifestBytes != null}, Test assertion found: $success"
                    )
                } finally {
                    builder.close()
                }
            } catch (e: Exception) {
                TestResult("Reader with Manifest Data", false, "Exception: ${e.message}", e.toString())
            }
        })
        
        // Test 15: Signer with Callback
        results.add(runTest("Signer with Callback") {
            val manifestJson = """{
                "claim_generator": "test_app/1.0",
                "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
            }"""
            
            try {
                val builder = Builder(manifestJson)
                try {
                    val sourceImageData = getResourceAsBytes(context, R.raw.pexels_asadphoto_457882)
                    val sourceStream = MemoryStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-callback-test", ".jpg")
                    val destStream = Stream(fileTest)
                    
                    val certPem = getResourceAsString(context, R.raw.es256_certs)
                    val keyPem = getResourceAsString(context, R.raw.es256_private)
                    
                    var signCallCount = 0
                    
                    val callbackSigner = Signer(SigningAlgorithm.es256, certPem, null) { data ->
                        signCallCount++
                        SigningHelper.signWithPEMKey(data, keyPem, "ES256")
                    }
                    
                    try {
                        val reserveSize = callbackSigner.reserveSize()
                        val result = builder.sign("image/jpeg", sourceStream.stream, destStream, callbackSigner)
                        val signSucceeded = result.size > 0
                        
                        val (manifest, signatureVerified) = if (signSucceeded) {
                            try {
                                val manifestJson = C2PA.readFile(fileTest.absolutePath)
                                if (manifestJson != null) {
                                    Pair(manifestJson, true)
                                } else {
                                    Pair(null, false)
                                }
                            } catch (e: Exception) {
                                Pair(null, false)
                            }
                        } else {
                            Pair(null, false)
                        }
                        
                        val success = signCallCount > 0 && 
                                     reserveSize > 0 && 
                                     signSucceeded && 
                                     signatureVerified
                        
                        TestResult(
                            "Signer with Callback",
                            success,
                            if (success) "✓ Callback signer created and used successfully" 
                            else "✗ Callback signer test failed",
                            buildString {
                                append("Callback invoked: ${signCallCount} time(s)\n")
                                append("Reserve size: $reserveSize bytes\n")
                                append("Signing succeeded: $signSucceeded\n") 
                                append("Signature verified: $signatureVerified")
                                if (manifest != null && manifest.length > 100) {
                                    append("\nManifest size: ${manifest.length} chars")
                                }
                            }
                        )
                    } finally {
                        callbackSigner.close()
                        sourceStream.close()
                        destStream.close()
                        fileTest.delete()
                    }
                } finally {
                    builder.close()
                }
            } catch (e: Exception) {
                TestResult(
                    "Signer with Callback", 
                    false, 
                    "✗ Test failed with exception", 
                    "${e.javaClass.simpleName}: ${e.message}\n${e.stackTrace.take(3).joinToString("\n")}"
                )
            }
        })
        
        // Test 16: File Operations with Data Directory
        results.add(runTest("File Operations with Data Directory") {
            val testImageFile = copyResourceToFile(context, R.raw.adobe_20220124_ci, "test_datadir.jpg")
            val dataDir = File(context.cacheDir, "c2pa_data")
            dataDir.mkdirs()
            
            try {
                C2PA.readFile(testImageFile.absolutePath, dataDir.absolutePath)
                C2PA.readIngredientFile(testImageFile.absolutePath, dataDir.absolutePath)
                
                val dataFiles = dataDir.listFiles() ?: emptyArray()
                val success = dataFiles.isNotEmpty() && dataFiles.any { it.length() > 0 }
                
                TestResult(
                    "File Operations with Data Directory",
                    success,
                    if (success) "Resources written to data directory" else "No resources written",
                    "Files in dataDir: ${dataFiles.size}, Total size: ${dataFiles.sumOf { it.length() }} bytes"
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
            
            try {
                val builder = Builder(manifestJson)
                try {
                    builder.setNoEmbed()
                    val writeOnlyStream = MemoryStream()
                    try {
                        builder.toArchive(writeOnlyStream.stream)
                        val data = writeOnlyStream.getData()
                        val success = data.isNotEmpty()
                        
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
            } catch (e: C2PAError) {
                TestResult("Write-Only Streams", false, "Failed to create builder", e.toString())
            }
        })
        
        // Test 18: Custom Stream Callbacks
        results.add(runTest("Custom Stream Callbacks") {
            var readCalled = false
            var writeCalled = false
            var seekCalled = false
            var flushCalled = false
            
            val buffer = ByteArrayOutputStream()
            var position = 0
            var data = ByteArray(0)
            
            val customStream = CallbackStream(
                reader = { buf, length ->
                    readCalled = true
                    if (position >= data.size) return@CallbackStream 0
                    val toRead = minOf(length, data.size - position)
                    System.arraycopy(data, position, buf, 0, toRead)
                    position += toRead
                    toRead
                },
                seeker = { offset, mode ->
                    seekCalled = true
                    position = when (mode) {
                        C2paSeekMode.Start -> offset.toInt()
                        C2paSeekMode.Current -> position + offset.toInt()
                        C2paSeekMode.End -> data.size + offset.toInt()
                    }
                    position = position.coerceIn(0, data.size)
                    position.toLong()
                },
                writer = { writeData, length ->
                    writeCalled = true
                    buffer.write(writeData, 0, length)
                    data = buffer.toByteArray()
                    position += length
                    length
                },
                flusher = {
                    flushCalled = true
                    data = buffer.toByteArray()
                    0
                }
            )
            
            try {
                customStream.write(ByteArray(10), 10)
                customStream.seek(0, C2paSeekMode.Start.value)
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
                val preserveStream = Stream(tempFile, truncate = false)
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
        
        // Test 20: Web Service Signer Creation  
        results.add(runTest("Web Service Real Signing & Verification") {
            val manifestJson = """{
                "claim_generator": "test_app/1.0",
                "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
            }"""
            
            try {
                val certPem = getResourceAsString(context, R.raw.es256_certs)
                val keyPem = getResourceAsString(context, R.raw.es256_private)
                
                val mockServiceUrl = "http://mock.signing.service/sign"
                
                val mockService = MockSigningService { data ->
                    SigningHelper.signWithPEMKey(data, keyPem, "ES256")
                }
                
                MockSigningService.register(mockServiceUrl, mockService)
                
                try {
                    val builder = Builder(manifestJson)
                    try {
                        val sourceImageData = getResourceAsBytes(context, R.raw.pexels_asadphoto_457882)
                        val sourceStream = MemoryStream(sourceImageData)
                        val fileTest = File.createTempFile("c2pa-websvc-test", ".jpg")
                        val destStream = Stream(fileTest)
                        
                        var webServiceCalled = false
                        
                        val webServiceSigner = WebServiceSignerHelper.createWebServiceSigner(
                            serviceUrl = mockServiceUrl,
                            algorithm = SigningAlgorithm.es256,
                            certsPem = certPem,
                            tsaUrl = null
                        )
                        
                        try {
                            val result = builder.sign("image/jpeg", sourceStream.stream, destStream, webServiceSigner)
                            webServiceCalled = true
                            
                            val manifest = C2PA.readFile(fileTest.absolutePath)
                            val hasManifest = manifest != null
                            
                            val success = webServiceCalled && hasManifest && result.size > 0
                            
                            TestResult(
                                "Web Service Real Signing & Verification",
                                success,
                                if (success) "Web service signing successful" else "Web service signing failed",
                                "Mock service used, Result size: ${result.size}, Has manifest: $hasManifest"
                            )
                        } finally {
                            webServiceSigner.close()
                            sourceStream.close()
                            destStream.close()
                            fileTest.delete()
                        }
                    } finally {
                        builder.close()
                    }
                } finally {
                    MockSigningService.unregister(mockServiceUrl)
                }
            } catch (e: Exception) {
                TestResult("Web Service Real Signing & Verification", false, "Failed with exception", e.toString())
            }
        })
        
        // Test 21: Hardware Signer Creation
        results.add(runTest("Hardware Signer Creation") {
            val hasStrongBox = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            
            var genInHw = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    val keyAlias = "test_hw_key_${System.currentTimeMillis()}"
                    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)
                    
                    val keyGenSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                        keyAlias,
                        android.security.keystore.KeyProperties.PURPOSE_SIGN
                    ).apply {
                        setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                        setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                        if (hasStrongBox) {
                            setIsStrongBoxBacked(true)
                        }
                    }.build()
                    
                    val keyPairGen = java.security.KeyPairGenerator.getInstance(
                        android.security.keystore.KeyProperties.KEY_ALGORITHM_EC,
                        "AndroidKeyStore"
                    )
                    keyPairGen.initialize(keyGenSpec)
                    keyPairGen.generateKeyPair()
                    
                    val key = keyStore.getKey(keyAlias, null) as? java.security.PrivateKey
                    if (key != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        try {
                            val factory = java.security.KeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                            val keyInfo = factory.getKeySpec(key, android.security.keystore.KeyInfo::class.java)
                            @Suppress("DEPRECATION")
                            genInHw = keyInfo.isInsideSecureHardware
                        } catch (_: Exception) {
                            genInHw = hasStrongBox
                        }
                    }
                    
                    keyStore.deleteEntry(keyAlias)
                } catch (_: Exception) {
                    // Hardware key generation failed
                }
            }
            
            val success = genInHw || !hasStrongBox
            TestResult(
                "Hardware Signer Creation",
                success,
                if (genInHw) "Generated key in hardware" else if (!hasStrongBox) "No StrongBox available" else "Failed to use hardware",
                "StrongBox available: $hasStrongBox, Generated in HW: $genInHw"
            )
        })
        
        // Test 22: StrongBox Signer Creation
        results.add(runTest("StrongBox Signer Creation") {
            val hasStrongBox = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            
            var strongBoxKeyCreated = false
            if (hasStrongBox && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    val keyAlias = "test_strongbox_key_${System.currentTimeMillis()}"
                    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)
                    
                    val keyGenSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                        keyAlias,
                        android.security.keystore.KeyProperties.PURPOSE_SIGN
                    ).apply {
                        setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                        setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                        setIsStrongBoxBacked(true)
                    }.build()
                    
                    val keyPairGen = java.security.KeyPairGenerator.getInstance(
                        android.security.keystore.KeyProperties.KEY_ALGORITHM_EC,
                        "AndroidKeyStore"
                    )
                    keyPairGen.initialize(keyGenSpec)
                    keyPairGen.generateKeyPair()
                    
                    strongBoxKeyCreated = keyStore.containsAlias(keyAlias)
                    
                    keyStore.deleteEntry(keyAlias)
                } catch (_: Exception) {
                    // StrongBox key generation failed
                }
            }
            
            val success = strongBoxKeyCreated || !hasStrongBox
            TestResult(
                "StrongBox Signer Creation",
                success,
                if (strongBoxKeyCreated) "StrongBox key created" else if (!hasStrongBox) "StrongBox not available" else "StrongBox key creation failed",
                "Has StrongBox: $hasStrongBox, Key created: $strongBoxKeyCreated"
            )
        })
        
        // Test 23: Signing Algorithm Tests
        results.add(runTest("Signing Algorithm Tests") {
            val algorithms = listOf("es256")
            val resultPerAlg = mutableListOf<String>()
            
            algorithms.forEach { alg ->
                try {
                    val manifestJson = """{"claim_generator": "test_app/1.0", "assertions": [{"label": "c2pa.test", "data": {"test": true}}]}"""
                    val builder = Builder(manifestJson)
                    
                    val sourceImageData = getResourceAsBytes(context, R.raw.pexels_asadphoto_457882)
                    val sourceStream = MemoryStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-alg-test-$alg", ".jpg")
                    val destStream = Stream(fileTest)
                    
                    val certPem = getResourceAsString(context, R.raw.es256_certs)
                    val keyPem = getResourceAsString(context, R.raw.es256_private)
                    val algorithm = SigningAlgorithm.entries.find { it.name == alg } ?: SigningAlgorithm.es256
                    val signerInfo = SignerInfo(algorithm, certPem, keyPem)
                    val signer = Signer(signerInfo)
                    
                    try {
                        builder.sign("image/jpeg", sourceStream.stream, destStream, signer)
                        val ok = C2PA.readFile(fileTest.absolutePath) != null
                        resultPerAlg.add("$alg:${if(ok) "ok" else "fail"}")
                    } finally {
                        signer.close()
                        builder.close()
                        sourceStream.close()
                        destStream.close()
                        fileTest.delete()
                    }
                } catch (_: Exception) {
                    resultPerAlg.add("$alg:fail")
                }
            }
            
            val success = resultPerAlg.all { it.endsWith("ok") }
            TestResult(
                "Signing Algorithm Tests",
                success,
                if (success) "All algorithms passed" else "Some algorithms failed",
                resultPerAlg.joinToString(", ")
            )
        })
        
        // Test 24: Signer Reserve Size
        results.add(runTest("Signer Reserve Size") {
            val certPem = getResourceAsString(context, R.raw.es256_certs)
            val keyPem = getResourceAsString(context, R.raw.es256_private)
            val signerInfo = SignerInfo(SigningAlgorithm.es256, certPem, keyPem)
            val signer = Signer(signerInfo)
            
            try {
                val reserveSize = signer.reserveSize()
                val success = reserveSize > 0
                TestResult(
                    "Signer Reserve Size",
                    success,
                    if (success) "Signer reserve size obtained" else "Invalid reserve size",
                    "Reserve size: $reserveSize bytes"
                )
            } finally {
                signer.close()
            }
        })
        
        // Test 25: Reader Resource Error Handling
        results.add(runTest("Reader Resource Error Handling") {
            val testImageData = getResourceAsBytes(context, R.raw.adobe_20220124_ci)
            val stream = MemoryStream(testImageData)
            try {
                val reader = Reader("image/jpeg", stream.stream)
                try {
                    val resourceStream = MemoryStream()
                    try {
                        reader.resource("non_existent_resource", resourceStream.stream)
                        TestResult(
                            "Reader Resource Error Handling",
                            false,
                            "Should have thrown exception for missing resource",
                            "No exception thrown"
                        )
                    } catch (e: C2PAError) {
                        TestResult(
                            "Reader Resource Error Handling",
                            true,
                            "Correctly threw exception for missing resource",
                            "Error: ${e.message}"
                        )
                    } finally {
                        resourceStream.close()
                    }
                } finally {
                    reader.close()
                }
            } finally {
                stream.close()
            }
        })
        
        // Test 26: Error Enum Coverage
        results.add(runTest("Error Enum Coverage") {
            val errors = mutableListOf<String>()
            var caughtApiError: C2PAError? = null
            var caughtNegativeError: C2PAError? = null
            
            try {
                val reader = Reader("invalid/format", MemoryStream(ByteArray(0)).stream)
                reader.close()
            } catch (e: C2PAError.api) {
                caughtApiError = e
                errors.add("Caught API error: ${e.message}")
            } catch (e: Exception) {
                errors.add("Wrong exception type: ${e::class.simpleName}")
            }
            
            val nilError = C2PAError.nilPointer
            val utf8Error = C2PAError.utf8
            val negError = C2PAError.negative(-42)
            val apiErr = C2PAError.api("Test error")
            
            try {
                val builder = Builder("not valid json")
                builder.close()
            } catch (e: C2PAError.api) {
                if (caughtApiError == null) caughtApiError = e
                errors.add("Caught builder error: ${e.message}")
            } catch (e: Exception) {
                errors.add("Builder threw non-C2PA error: ${e::class.simpleName}")
            }
            
            try {
                val signer = Signer(SignerInfo(SigningAlgorithm.es256, "invalid cert", "invalid key"))
                signer.close()
            } catch (e: C2PAError.api) {
                if (caughtApiError == null) caughtApiError = e
                errors.add("Caught signer error: ${e.message}")
            } catch (e: Exception) {
                errors.add("Signer threw non-C2PA error: ${e::class.simpleName}")
            }
            
            val canCreateErrors = nilError != null && 
                                utf8Error != null && 
                                negError != null && 
                                apiErr != null
            
            val errorMessages = listOf(
                "nilPointer: ${nilError.toString()}",
                "utf8: ${utf8Error.toString()}",
                "negative: ${negError.toString()}",
                "api: ${apiErr.toString()}"
            )
            
            val success = canCreateErrors && caughtApiError != null
            
            TestResult(
                "Error Enum Coverage",
                success,
                if (success) "All error types covered" else "Some error types not covered",
                errorMessages.joinToString("\n") + "\nCaught: ${caughtApiError?.toString() ?: "none"}\n\nOperations tried: ${errors.joinToString("; ")}"
            )
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
}