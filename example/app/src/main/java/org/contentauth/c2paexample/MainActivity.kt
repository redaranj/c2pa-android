package org.contentauth.c2paexample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import org.contentauth.c2paexample.ui.theme.C2PAExampleTheme

private const val TAG = "C2PAExample"

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
