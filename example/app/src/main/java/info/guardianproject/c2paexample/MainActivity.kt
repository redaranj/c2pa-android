package info.guardianproject.c2paexample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import info.guardianproject.c2pa.C2PA
import info.guardianproject.c2paexample.ui.theme.C2PAExampleTheme
import kotlinx.coroutines.launch

private const val TAG = "C2PAExample"

data class TestResult(
    val name: String,
    val success: Boolean,
    val message: String,
    val details: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            C2PAExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    C2PATestScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun C2PATestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testResults by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var libraryVersion by remember { mutableStateOf("Loading...") }
    
    LaunchedEffect(Unit) {
        libraryVersion = try {
            C2PA.version()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "C2PA Android Test Suite",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Library Version: $libraryVersion",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (!isRunning) {
                    scope.launch {
                        isRunning = true
                        testResults = emptyList()
                        try {
                            val simpleResults = SimpleTest.runBasicTests(context)
                            testResults = simpleResults.mapIndexed { index, result ->
                                TestResult(
                                    name = "Test ${index + 1}",
                                    success = result.startsWith("✓"),
                                    message = result,
                                    details = null
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error running tests", e)
                            testResults = listOf(
                                TestResult(
                                    name = "Test Execution Error",
                                    success = false,
                                    message = "Failed to run test suite: ${e.message}",
                                    details = e.toString()
                                )
                            )
                        }
                        isRunning = false
                    }
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRunning) "Running Tests..." else "Run All Tests")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (testResults.isNotEmpty()) {
            val passedCount = testResults.count { it.success }
            val totalCount = testResults.size
            
            Text(
                text = "Results: $passedCount/$totalCount tests passed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (passedCount == totalCount) Color.Green else Color.Red
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        LazyColumn {
            items(testResults) { result ->
                TestResultCard(result = result)
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
            containerColor = if (result.success) 
                Color.Green.copy(alpha = 0.1f) 
            else 
                Color.Red.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "${if (result.success) "✓" else "✗"} ${result.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (result.success) Color.Green else Color.Red
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyMedium
            )
            
            result.details?.let { details ->
                if (details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Details:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
