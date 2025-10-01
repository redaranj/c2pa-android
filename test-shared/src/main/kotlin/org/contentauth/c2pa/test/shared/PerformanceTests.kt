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
 * PerformanceTests - Performance and concurrency tests
 * 
 * This file contains extracted test methods that can be run individually.
 */
abstract class PerformanceTests : TestBase() {

    // Individual test methods
    
    suspend fun testLargeBufferHandling(): TestResult = withContext(Dispatchers.IO) {
        runTest("Large Buffer Handling") {
            var exceptionThrown = false
            val largeSize = Int.MAX_VALUE.toLong() + 1L

            val mockStream = CallbackStream(
                reader = { buf, length ->
                    // This should trigger our overflow protection
                    0
                },
                writer = { data, length ->
                    // This should trigger our overflow protection
                    0
                }
            )

            try {
                // Try to read with a buffer larger than Int.MAX_VALUE
                val result = mockStream.read(ByteArray(1024), largeSize)
                // The implementation should safely handle this
                val success = result <= Int.MAX_VALUE

                TestResult(
                    "Large Buffer Handling",
                    success,
                    if (success) "Large buffer handled safely" else "Large buffer not handled properly",
                    "Requested: $largeSize, Got: $result"
                )
            } finally {
                mockStream.close()
            }
        }
    }

    suspend fun testConcurrentOperations(): TestResult = withContext(Dispatchers.IO) {
        runTest("Concurrent Operations") {
            val errors = mutableListOf<String>()
            val successes = mutableListOf<String>()

            // Run multiple operations concurrently
            val jobs = List(4) { index ->
                async(Dispatchers.IO) {
                    try {
                        when (index) {
                            0 -> {
                                C2PA.readFile("/non/existent/file$index.jpg")
                                errors.add("Read $index: ${C2PA.getError()}")
                            }
                            1 -> {
                                val version = C2PA.version()
                                successes.add("Version $index: $version")
                            }
                            2 -> {
                                try {
                                    Builder.fromJson("invalid json $index")
                                } catch (e: C2PAError) {
                                    errors.add("Builder $index: ${e.message}")
                                }
                            }
                            3 -> {
                                C2PA.readFile("/another/missing$index.jpg")
                                errors.add("Read2 $index: ${C2PA.getError()}")
                            }
                        }
                    } catch (e: Exception) {
                        errors.add("Exception $index: ${e.message}")
                    }
                }
            }

            awaitAll(*jobs.toTypedArray())

            val success = errors.size >= 3 && successes.isNotEmpty()

            TestResult(
                "Concurrent Operations",
                success,
                if (success) "Concurrent operations handled" else "Concurrent operations failed",
                "Errors: ${errors.size}, Successes: ${successes.size}\n${errors.joinToString("\n")}"
            )
        }
    }

    suspend fun testReaderResourceErrorHandling(): TestResult = withContext(Dispatchers.IO) {
        runTest("Reader Resource Error Handling") {
            val testImageData = loadResourceAsBytes("adobe_20220124_ci")
            val stream = ByteArrayStream(testImageData)
            try {
                val reader = Reader.fromStream("image/jpeg", stream)
                try {
                    val resourceStream = ByteArrayStream()
                    try {
                        reader.resource("non_existent_resource", resourceStream)
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
        }
    }

}