package com.luparey.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

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

enum class Filtro(val etiqueta: String, val valores: FloatArray) {
    NORMAL(
        "Normal",
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    CONTRASTE(
        "Contraste",
        floatArrayOf(
            2f, 0f, 0f, 0f, -127.5f,
            0f, 2f, 0f, 0f, -127.5f,
            0f, 0f, 2f, 0f, -127.5f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    GRISES(
        "Grises",
        floatArrayOf(
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    INVERTIR(
        "Invertir",
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

fun aplicarFiltro(origen: Bitmap, filtro: Filtro): Bitmap {
    val salida = Bitmap.createBitmap(origen.width, origen.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(salida)
    val paint = android.graphics.Paint().apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(
            android.graphics.ColorMatrix(filtro.valores)
        )
    }
    canvas.drawBitmap(origen, 0f, 0f, paint)
    return salida
}

fun imagenAMapa(imagen: ImageProxy): Bitmap {
    val buffer = imagen.planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val giro = imagen.imageInfo.rotationDegrees
    if (giro == 0) return bitmap
    val matriz = Matrix().apply { postRotate(giro.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
}

fun guardarEnGaleria(context: Context, bitmap: Bitmap): Boolean {
    val valores = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "luparey_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/luparey")
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
        ?: return false
    return context.contentResolver.openOutputStream(uri)?.use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
    } ?: false
}

@Composable
fun FiltrosFila(seleccionado: Filtro, alElegir: (Filtro) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        Filtro.entries.forEach { f ->
            FilterChip(
                selected = f == seleccionado,
                onClick = { alElegir(f) },
                label = { Text(f.etiqueta) }
            )
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
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(4f) }
    var torchOn by remember { mutableStateOf(false) }
    var filtro by remember { mutableStateOf(Filtro.NORMAL) }
    var fotoCongelada by remember { mutableStateOf<Bitmap?>(null) }
    var longitudBarraDp by remember { mutableFloatStateOf(120f) }
    var dpPorMmAZoom1 by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(zoom, camera) { camera?.cameraControl?.setZoomRatio(zoom) }
    LaunchedEffect(torchOn, camera) { camera?.cameraControl?.enableTorch(torchOn) }

    val filtrada = remember(fotoCongelada, filtro) {
        fotoCongelada?.let { aplicarFiltro(it, filtro) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            if (filtrada != null) {
                Image(
                    bitmap = filtrada.asImageBitmap(),
                    contentDescription = "Imagen congelada",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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
                                val captura = ImageCapture.Builder().build()
                                provider.unbindAll()
                                val enlazada = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    captura
                                )
                                camera = enlazada
                                imageCapture = captura
                                enlazada.cameraInfo.zoomState.value?.let {
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

                // Barra de medida: su longitud en mm se calcula con la calibración
                val milimetros = dpPorMmAZoom1?.let { longitudBarraDp / (it * zoom) }
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(modifier = Modifier.size(width = longitudBarraDp.dp, height = 12.dp)) {
                        val y = size.height / 2f
                        drawLine(Color.White, Offset(0f, y), Offset(size.width, y), strokeWidth = 4f)
                        drawLine(Color.White, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 4f)
                        drawLine(
                            Color.White,
                            Offset(size.width, 0f),
                            Offset(size.width, size.height),
                            strokeWidth = 4f
                        )
                    }
                    Text(
                        text = milimetros?.let { "%.2f mm".format(it) } ?: "Sin calibrar",
                        color = Color.White
                    )
                }
            }

            // Controles
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filtrada == null) {
                    Text("Zoom ${"%.1f".format(zoom)}x", color = Color.White)
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = minZoom..maxZoom,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Barra de medida: ${longitudBarraDp.toInt()} dp", color = Color.White)
                    Slider(
                        value = longitudBarraDp,
                        onValueChange = { longitudBarraDp = it },
                        valueRange = 40f..300f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { dpPorMmAZoom1 = longitudBarraDp / zoom },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Fijar 1 mm")
                        }
                        Button(
                            onClick = { torchOn = !torchOn },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (torchOn) "Apagar luz" else "Linterna")
                        }
                    }
                    Button(
                        onClick = {
                            imageCapture?.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        fotoCongelada = imagenAMapa(image)
                                        image.close()
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Toast.makeText(
                                            context,
                                            "No se pudo capturar",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Congelar")
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { fotoCongelada = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Volver")
                        }
                        Button(
                            onClick = {
                                val ok = guardarEnGaleria(context, filtrada)
                                Toast.makeText(
                                    context,
                                    if (ok) "Guardada en Pictures/luparey" else "No se pudo guardar",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Guardar")
                        }
                    }
                    FiltrosFila(seleccionado = filtro, alElegir = { filtro = it })
                }
            }
        } else {
            Text(
                text = "luparey necesita permiso de cámara para funcionar.",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }
    }
}
