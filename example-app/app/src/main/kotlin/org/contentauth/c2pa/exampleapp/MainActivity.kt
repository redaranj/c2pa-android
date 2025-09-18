package org.contentauth.c2pa.exampleapp

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.contentauth.c2pa.exampleapp.data.C2PAManager
import org.contentauth.c2pa.exampleapp.data.PreferencesManager
import org.contentauth.c2pa.exampleapp.ui.camera.CameraScreen
import org.contentauth.c2pa.exampleapp.ui.gallery.GalleryScreen
import org.contentauth.c2pa.exampleapp.ui.settings.SettingsScreen
import org.contentauth.c2pa.exampleapp.ui.theme.ExampleTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var c2paManager: C2PAManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesManager = PreferencesManager(this)
        c2paManager = C2PAManager(this, preferencesManager)

        setContent {
            ExampleTheme {
                C2PAExampleApp(
                        preferencesManager = preferencesManager,
                        c2paManager = c2paManager,
                        onImageSigned = { signedImagePath ->
                            runOnUiThread {
                                Toast.makeText(this, "Image signed and saved", Toast.LENGTH_SHORT)
                                        .show()
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun C2PAExampleApp(
        preferencesManager: PreferencesManager,
        c2paManager: C2PAManager,
        onImageSigned: (String) -> Unit
) {
    val navController = rememberNavController()
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "camera") {
            composable("camera") {
                CameraScreen(
                        onNavigateToSettings = { navController.navigate("settings") },
                        onNavigateToGallery = { navController.navigate("gallery") },
                        onImageCaptured = { bitmap, location ->
                            isProcessing = true
                            processingMessage = "Signing image with C2PA..."

                            // Process in coroutine
                            kotlinx.coroutines.GlobalScope.launch {
                                try {
                                    val result = c2paManager.signImage(bitmap, location)
                                    result.fold(
                                            onSuccess = { signedBytes ->
                                                processingMessage = "Saving to gallery..."

                                                val saveResult =
                                                        c2paManager.saveImageToGallery(signedBytes)
                                                saveResult.fold(
                                                        onSuccess = { path ->
                                                            onImageSigned(path)
                                                            navController.navigate("gallery")
                                                        },
                                                        onFailure = { error ->
                                                            Log.e(
                                                                    "MainActivity",
                                                                    "Failed to save image",
                                                                    error
                                                            )
                                                        }
                                                )
                                            },
                                            onFailure = { error ->
                                                Log.e("MainActivity", "Failed to sign image", error)
                                            }
                                    )
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                )
            }

            composable("settings") {
                SettingsScreen(
                        preferencesManager = preferencesManager,
                        onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("gallery") {
                GalleryScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onImageSelected = { file ->
                            // Handle image selection if needed
                        }
                )
            }
        }

        // Processing overlay
        if (isProcessing) {
            Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card {
                        Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                    text = processingMessage,
                                    style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
