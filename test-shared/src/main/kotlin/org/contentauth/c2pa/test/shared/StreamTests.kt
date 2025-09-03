package org.contentauth.c2pa.test.shared

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * StreamTests - Stream operations and I/O tests
 * 
 * This file contains extracted test methods that can be run individually.
 */
abstract class StreamTests : TestBase() {

    // Individual test methods
    
    suspend fun testStreamOperations(): TestResult = withContext(Dispatchers.IO) {
        runTest("Stream API") {
            val testImageData = loadResourceAsBytes("adobe_20220124_ci")
            val memStream = ByteArrayStream(testImageData)
            try {
                val reader = Reader.fromStream("image/jpeg", memStream)
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
        }
    }

    suspend fun testStreamFileOptions(): TestResult = withContext(Dispatchers.IO) {
        runTest("Stream File Options") {
            val tempFile = File.createTempFile("file-stream-preserve", ".dat", getContext().cacheDir)
            tempFile.writeBytes(ByteArray(100) { it.toByte() })

            try {
                val preserveStream = FileStream(tempFile, FileStream.Mode.READ_WRITE, createIfNeeded = false)
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
        }
    }

    suspend fun testWriteOnlyStreams(): TestResult = withContext(Dispatchers.IO) {
        runTest("Write-Only Streams") {
            val manifestJson = """{
                "claim_generator": "test_app/1.0",
                "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
            }"""

            try {
                val builder = Builder.fromJson(manifestJson)
                try {
                    builder.setNoEmbed()
                    val writeOnlyStream = ByteArrayStream()
                    try {
                        builder.toArchive(writeOnlyStream)
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
        }
    }

    suspend fun testCustomStreamCallbacks(): TestResult = withContext(Dispatchers.IO) {
        runTest("Custom Stream Callbacks") {
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
                        SeekMode.START -> offset.toInt()
                        SeekMode.CURRENT -> position + offset.toInt()
                        SeekMode.END -> data.size + offset.toInt()
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
        }
    }

    suspend fun testFileOperationsWithDataDirectory(): TestResult = withContext(Dispatchers.IO) {
        runTest("File Operations with Data Directory") {
            val testImageFile = copyResourceToFile("adobe_20220124_ci", "test_datadir.jpg")
            val dataDir = File(getContext().cacheDir, "c2pa_data")
            dataDir.mkdirs()

            try {
                try {
                    C2PA.readFile(testImageFile.absolutePath, dataDir.absolutePath)
                } catch (e: C2PAError) {
                    // May fail if no manifest
                }

                try {
                    C2PA.readIngredientFile(testImageFile.absolutePath, dataDir.absolutePath)
                } catch (e: C2PAError) {
                    // May fail if no ingredient
                }

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
        }
    }

}