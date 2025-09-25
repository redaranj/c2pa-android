package org.contentauth.c2pa.exampleapp.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.C2PA

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
        onNavigateBack: () -> Unit,
        onImageSelected: (File) -> Unit,
        modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
    var selectedImage by remember { mutableStateOf<GalleryImage?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val photosDir =
                    File(
                            context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
                            "C2PA"
                    )
            android.util.Log.d("GalleryScreen", "Looking for images in: ${photosDir.absolutePath}")
            android.util.Log.d("GalleryScreen", "Directory exists: ${photosDir.exists()}")

            if (photosDir.exists()) {
                val allFiles = photosDir.listFiles()
                android.util.Log.d(
                        "GalleryScreen",
                        "Total files in directory: ${allFiles?.size ?: 0}"
                )
                allFiles?.forEach { file ->
                    android.util.Log.d(
                            "GalleryScreen",
                            "Found file: ${file.name} (${file.length()} bytes)"
                    )
                }

                val imageFiles =
                        photosDir
                                .listFiles { file ->
                                    val isImage =
                                            file.extension.lowercase() in
                                                    listOf("jpg", "jpeg", "png")
                                    android.util.Log.d(
                                            "GalleryScreen",
                                            "File ${file.name} is image: $isImage"
                                    )
                                    isImage
                                }
                                ?.sortedByDescending { it.lastModified() }
                                ?: emptyList()

                android.util.Log.d("GalleryScreen", "Found ${imageFiles.size} image files")

                images =
                        imageFiles.map { file ->
                            val hasC2PA =
                                    try {
                                        C2PA.readFile(file.absolutePath, null)
                                        android.util.Log.d(
                                                "GalleryScreen",
                                                "File ${file.name} has C2PA: true"
                                        )
                                        true
                                    } catch (e: Exception) {
                                        android.util.Log.d(
                                                "GalleryScreen",
                                                "File ${file.name} has C2PA: false - ${e.message}"
                                        )
                                        false
                                    }
                            GalleryImage(file, hasC2PA)
                        }
            } else {
                android.util.Log.d("GalleryScreen", "Gallery directory does not exist")
            }
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Gallery") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                )
                            }
                        },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        titleContentColor =
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                )
                )
            }
    ) { paddingValues ->
        if (selectedImage != null) {
            ImageDetailView(
                    image = selectedImage!!,
                    onDismiss = { selectedImage = null },
                    modifier = Modifier.padding(paddingValues)
            )
        } else {
            if (images.isEmpty()) {
                Box(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                                text = "No images yet",
                                style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text = "Take a photo to get started",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = modifier.fillMaxSize().padding(paddingValues),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(images) { image ->
                        ImageThumbnail(image = image, onClick = { selectedImage = image })
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageThumbnail(image: GalleryImage, onClick: () -> Unit) {
    Box(modifier = Modifier.aspectRatio(1f).clickable { onClick() }) {
        AsyncImage(
                model = image.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
        )

        if (image.hasC2PA) {
            Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
            ) {
                Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "C2PA Verified",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp).size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ImageDetailView(
        image: GalleryImage,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier
) {
    var manifestInfo by remember { mutableStateOf<ManifestInfo?>(null) }

    LaunchedEffect(image) {
        withContext(Dispatchers.IO) {
            if (image.hasC2PA) {
                try {
                    val manifestJSON = C2PA.readFile(image.file.absolutePath, null)
                    val manifest = org.json.JSONObject(manifestJSON)

                    val activeManifest = manifest.optJSONObject("active_manifest")
                    manifestInfo =
                            if (activeManifest != null) {
                                ManifestInfo(
                                        claimGenerator =
                                                activeManifest.optString("claim_generator"),
                                        title = activeManifest.optString("title"),
                                        algorithm =
                                                activeManifest
                                                        .optJSONObject("signature_info")
                                                        ?.optString("alg"),
                                        issuer =
                                                activeManifest
                                                        .optJSONObject("signature_info")
                                                        ?.optString("issuer"),
                                        time =
                                                activeManifest
                                                        .optJSONObject("signature_info")
                                                        ?.optString("time")
                                )
                            } else null
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Image
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AsyncImage(
                    model = image.file,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
            )

            // Close button
            Surface(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        modifier = Modifier.padding(8.dp)
                )
            }
        }

        // Metadata
        if (image.hasC2PA && manifestInfo != null) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = "C2PA Verified",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    manifestInfo?.let { info ->
                        if (info.title.isNotEmpty()) {
                            InfoRow("Title", info.title)
                        }
                        if (info.claimGenerator.isNotEmpty()) {
                            InfoRow("Generator", info.claimGenerator)
                        }
                        if (!info.algorithm.isNullOrEmpty()) {
                            InfoRow("Algorithm", info.algorithm)
                        }
                        if (!info.issuer.isNullOrEmpty()) {
                            InfoRow("Issuer", info.issuer)
                        }
                        if (!info.time.isNullOrEmpty()) {
                            InfoRow("Signed", info.time)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                            text = image.file.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
                text = "$label:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(80.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

private data class GalleryImage(val file: File, val hasC2PA: Boolean)

private data class ManifestInfo(
        val claimGenerator: String,
        val title: String,
        val algorithm: String?,
        val issuer: String?,
        val time: String?
)
