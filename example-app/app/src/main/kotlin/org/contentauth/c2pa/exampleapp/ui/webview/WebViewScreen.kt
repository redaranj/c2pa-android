package org.contentauth.c2pa.exampleapp.ui.webview

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    // Custom file chooser launcher that opens our C2PA-only file chooser
    val fileChooserLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri -> filePathCallback?.onReceiveValue(arrayOf(uri)) }
                            ?: filePathCallback?.onReceiveValue(null)
                } else {
                    filePathCallback?.onReceiveValue(null)
                }
                filePathCallback = null
            }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Check C2PA") },
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
        AndroidView(
                modifier = modifier.fillMaxSize().padding(paddingValues),
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                        }

                        webViewClient = WebViewClient()

                        webChromeClient =
                                object : WebChromeClient() {
                                    override fun onShowFileChooser(
                                            webView: WebView?,
                                            callback: ValueCallback<Array<Uri>>?,
                                            fileChooserParams: FileChooserParams?
                                    ): Boolean {
                                        filePathCallback = callback

                                        // Launch our custom C2PA file chooser activity
                                        val intent =
                                                Intent(context, C2PAFileChooserActivity::class.java)
                                        fileChooserLauncher.launch(intent)

                                        return true
                                    }
                                }

                        loadUrl("https://check.proofmode.org")
                        webView = this
                    }
                }
        )
    }
}
