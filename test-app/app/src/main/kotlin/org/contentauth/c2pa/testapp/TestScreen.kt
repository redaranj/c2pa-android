package org.contentauth.c2pa.testapp

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import org.contentauth.c2pa.test.shared.TestSuiteCore
import java.io.File

// Type alias for convenience
typealias TestResult = TestSuiteCore.TestResult

/**
 * Android-specific test suite implementation embedded in the test screen.
 * Extends TestSuiteCore to provide Android resource loading.
 */
private class AndroidTestSuite(private val context: Context) : TestSuiteCore() {
    
    override fun getContext(): Context = context
    
    override fun loadResourceAsBytes(resourceName: String): ByteArray {
        // First try to load from shared resources
        val sharedResource = TestSuiteCore.loadSharedResourceAsBytes("$resourceName.jpg")
            ?: TestSuiteCore.loadSharedResourceAsBytes("$resourceName.pem")
            ?: TestSuiteCore.loadSharedResourceAsBytes("$resourceName.key")
        
        return sharedResource ?: throw IllegalArgumentException("Resource not found: $resourceName")
    }
    
    override fun loadResourceAsString(resourceName: String): String {
        // First try to load from shared resources
        val sharedResource = TestSuiteCore.loadSharedResourceAsString("$resourceName.jpg")
            ?: TestSuiteCore.loadSharedResourceAsString("$resourceName.pem")
            ?: TestSuiteCore.loadSharedResourceAsString("$resourceName.key")
        
        return sharedResource ?: throw IllegalArgumentException("Resource not found: $resourceName")
    }
    
    override fun copyResourceToFile(resourceName: String, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        val resourceBytes = loadResourceAsBytes(resourceName)
        file.writeBytes(resourceBytes)
        return file
    }
}

@Composable
fun TestScreen(modifier: Modifier = Modifier) {
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
                    val testSuite = AndroidTestSuite(context)
                    testResults = testSuite.runAllTests()
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
                Color(0xFFE8F5E9) // Very light green color
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
