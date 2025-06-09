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
import info.guardianproject.c2pa.C2PA
import info.guardianproject.c2pa.C2PABuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    try {
        val version = C2PA.version()
        results.add(TestResult(
            name = "Library Version",
            success = version.isNotEmpty(),
            message = "C2PA version: $version",
            details = version
        ))
    } catch (e: Exception) {
        results.add(TestResult(
            name = "Library Version",
            success = false,
            message = "Failed to get version: ${e.message}",
            details = e.stackTraceToString()
        ))
    }
    
    // Test 2: Initial Error State
    try {
        val error = C2PA.getError()
        results.add(TestResult(
            name = "Initial Error State",
            success = error == null,
            message = if (error == null) "No initial errors" else "Error present: $error",
            details = error
        ))
    } catch (e: Exception) {
        results.add(TestResult(
            name = "Initial Error State",
            success = false,
            message = "Failed to check error: ${e.message}"
        ))
    }
    
    // Test 3: Load Settings
    try {
        val settings = """{"verify_trust": true}"""
        val result = C2PA.loadSettings(settings, "json")
        results.add(TestResult(
            name = "Load Settings",
            success = result == 0,
            message = if (result == 0) "Settings loaded successfully" else "Failed to load settings",
            details = "Settings: $settings\nResult: $result"
        ))
    } catch (e: Exception) {
        results.add(TestResult(
            name = "Load Settings",
            success = false,
            message = "Exception loading settings: ${e.message}"
        ))
    }
    
    // Test 4: Read Test Image
    try {
        // Copy test image to cache
        val testImageFile = File(context.cacheDir, "test_image.jpg")
        context.resources.openRawResource(R.raw.test_image).use { input ->
            testImageFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        val manifest = C2PA.readFile(testImageFile.absolutePath)
        if (manifest != null) {
            results.add(TestResult(
                name = "Read C2PA Manifest",
                success = true,
                message = "Successfully read manifest from test image",
                details = manifest.take(500) + if (manifest.length > 500) "..." else ""
            ))
        } else {
            val error = C2PA.getError()
            results.add(TestResult(
                name = "Read C2PA Manifest",
                success = false,
                message = "Failed to read manifest",
                details = error ?: "No error message available"
            ))
        }
    } catch (e: Exception) {
        results.add(TestResult(
            name = "Read C2PA Manifest",
            success = false,
            message = "Exception reading manifest: ${e.message}",
            details = e.stackTraceToString()
        ))
    }
    
    // Test 5: Read Non-existent File
    try {
        val result = C2PA.readFile("/non/existent/file.jpg")
        val error = C2PA.getError()
        results.add(TestResult(
            name = "Error Handling (Non-existent File)",
            success = result == null && error != null,
            message = if (result == null && error != null) {
                "Correctly handled missing file"
            } else {
                "Unexpected behavior for missing file"
            },
            details = "Result: $result\nError: $error"
        ))
    } catch (e: Exception) {
        results.add(TestResult(
            name = "Error Handling (Non-existent File)",
            success = false,
            message = "Exception handling missing file: ${e.message}"
        ))
    }
    
    // Test 6: C2PABuilder
    try {
        val manifestJson = """{"assertions": [{"label": "c2pa.test", "data": {"test": true}}]}"""
        val builder = C2PABuilder.fromJson(manifestJson)
        if (builder != null) {
            builder.close()
            results.add(TestResult(
                name = "C2PABuilder Creation",
                success = true,
                message = "Successfully created and used C2PABuilder"
            ))
        } else {
            results.add(TestResult(
                name = "C2PABuilder Creation",
                success = false,
                message = "Failed to create C2PABuilder"
            ))
        }
    } catch (e: Exception) {
        results.add(TestResult(
            name = "ManifestBuilder Creation",
            success = false,
            message = "Failed to create ManifestBuilder: ${e.message}",
            details = e.stackTraceToString()
        ))
    }
    
    results
}