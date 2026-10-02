package com.focustag.app.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import android.view.WindowManager
import android.view.Surface
import androidx.camera.core.ImageProxy
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.focustag.app.domain.TagLinkParser
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val Ink = Color(0xFF0B1F2A)
private val InkRaised = Color(0xFF123041)
private val Teal = Color(0xFF2ED3C6)
private val Sand = Color(0xFFF4EFE6)

@Composable
fun QrScanScreen(
    isFocusActive: Boolean,
    onUidResolved: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCamera = granted
        if (!granted) error = "Allow camera to scan."
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(20.dp)
    ) {
        Text(
            text = if (isFocusActive) "Scan to leave" else "Scan the door",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Text(
            text = "Point at the classroom code.",
            style = MaterialTheme.typography.bodyMedium,
            color = Sand.copy(alpha = 0.72f),
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
        if (hasCamera) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(InkRaised)
            ) {
                CameraBarcodePreview(
                    onRaw = { raw ->
                        val uid = TagLinkParser.extractUid(raw)
                        if (uid == null) error = "Not a classroom code"
                        else onUidResolved(uid)
                    },
                    onError = { error = it }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(220.dp)
                        .border(3.dp, Teal, RoundedCornerShape(20.dp))
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(28.dp)).background(InkRaised),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            }
        }
        error?.let {
            Text(text = it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sand, contentColor = Ink)
        ) { Text("Back") }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraBarcodePreview(onRaw: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val consumed = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
        },
        update = { previewView ->
            if (previewView.tag == "bound") return@AndroidView
            previewView.tag = "bound"
            previewView.post {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        val cameraProvider = future.get()
                        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                        val preview = Preview.Builder()
                            .setTargetRotation(rotation)
                            .build()
                            .also { it.surfaceProvider = previewView.surfaceProvider }
                        val scanner = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                        )
                        val analysis = ImageAnalysis.Builder()
                            .setTargetRotation(rotation)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()
                        analysis.setAnalyzer(executor) { imageProxy ->
                            readCode(imageProxy, scanner, consumed, mainExecutor, onRaw)
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    } catch (e: Exception) {
                        Log.e("QrScanScreen", "Camera bind failed", e)
                        previewView.tag = null
                        onError("Camera did not start. Try again.")
                    }
                }, mainExecutor)
            }
        }
    )
}

@OptIn(ExperimentalGetImage::class)
private fun readCode(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    consumed: AtomicBoolean,
    mainExecutor: java.util.concurrent.Executor,
    onRaw: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || consumed.get()) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener(mainExecutor) { barcodes ->
            val raw = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
            if (raw.isNotBlank() && consumed.compareAndSet(false, true)) onRaw(raw)
        }
        .addOnFailureListener { Log.e("QrScanScreen", "Scan failed", it) }
        .addOnCompleteListener { imageProxy.close() }
}
