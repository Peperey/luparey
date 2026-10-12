package com.luparey.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LuparyScreen()
            }
        }
    }
}

@Composable
fun LuparyScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(4f) }
    var torchOn by remember { mutableStateOf(false) }

    // Aplica zoom y linterna cada vez que cambian
    LaunchedEffect(zoom, camera) { camera?.cameraControl?.setZoomRatio(zoom) }
    LaunchedEffect(torchOn, camera) { camera?.cameraControl?.enableTorch(torchOn) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            provider.unbindAll()
                            val bound = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview
                            )
                            camera = bound
                            bound.cameraInfo.zoomState.value?.let {
                                minZoom = it.minZoomRatio
                                maxZoom = it.maxZoomRatio
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                }
            )

            // Marco circular tipo lupa
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .border(4.dp, Color(0xFFF2A541), CircleShape)
            )
        } else {
            Text(
                text = "luparey necesita permiso de cámara para funcionar.",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }

        // Controles
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Zoom ${"%.1f".format(zoom)}x",
                color = Color.White
            )
            Slider(
                value = zoom,
                onValueChange = { zoom = it },
                valueRange = minZoom..maxZoom,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { torchOn = !torchOn }) {
                Text(if (torchOn) "Apagar linterna" else "Encender linterna")
            }
        }
    }
}
