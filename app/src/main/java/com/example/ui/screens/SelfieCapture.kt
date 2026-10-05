package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * Full-screen selfie camera: front camera, an oval to center the face in, and tips
 * (good light, no hats / sunglasses / face coverings). Returns the photo as a Bitmap.
 */
@Composable
fun SelfieCaptureDialog(
    pose: String,
    onCaptured: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var denied by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        denied = !granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val lifecycleOwner = remember(context) {
        var c = context
        while (c is android.content.ContextWrapper && c !is LifecycleOwner) c = c.baseContext
        c as? LifecycleOwner
    }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }

    DisposableEffect(hasPermission, lifecycleOwner) {
        val owner = lifecycleOwner
        val providerFuture = ProcessCameraProvider.getInstance(context)
        if (hasPermission && owner != null) {
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture)
                } catch (e: Exception) {
                    problem = "Could not start the camera"
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            try {
                providerFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (hasPermission) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                // Dark overlay with an oval hole to center the face in
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val ovalW = size.width * 0.72f
                    val ovalH = ovalW * 1.3f
                    val left = (size.width - ovalW) / 2f
                    val top = (size.height - ovalH) / 2f - 30.dp.toPx()
                    val path = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addOval(Rect(left, top, left + ovalW, top + ovalH))
                    }
                    drawPath(path, Color.Black.copy(alpha = 0.6f))
                    drawOval(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = Size(ovalW, ovalH),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            } else {
                Text(
                    if (denied) "Camera permission is needed to take your selfie. Allow it in Settings, then try again."
                    else "Waiting for camera permission...",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            }

            // Tips at the top
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Face the camera in a well-lit place.\nRemove hats, sunglasses and face coverings.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Center your face in the oval. Then: $pose",
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            problem?.let {
                Text(
                    it,
                    color = Color(0xFFFF8A80),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp)
                )
            }

            // Shutter button
            if (hasPermission) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp)
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(8.dp)
                        .background(if (capturing) Color.Gray else Color.White, CircleShape)
                ) {
                    Button(
                        onClick = {
                            if (capturing) return@Button
                            capturing = true
                            imageCapture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bmp = try {
                                            val raw = image.toBitmap()
                                            val rotation = image.imageInfo.rotationDegrees
                                            if (rotation != 0) {
                                                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                                                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                                            } else raw
                                        } catch (e: Exception) {
                                            null
                                        } finally {
                                            image.close()
                                        }
                                        capturing = false
                                        if (bmp != null) onCaptured(bmp) else problem = "Could not take the photo"
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        capturing = false
                                        problem = "Could not take the photo"
                                    }
                                }
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {}
                }
            }
        }
    }
}
