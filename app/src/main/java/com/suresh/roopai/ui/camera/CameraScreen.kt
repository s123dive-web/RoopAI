package com.suresh.roopai.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.suresh.roopai.ui.common.EmptyState
import com.suresh.roopai.ui.common.openAppSettings
import java.io.File

/**
 * CameraX capture screen. The photo is written to the app cache and handed to [onCaptured] as a
 * file URI; nothing is added to the gallery until the user saves an edit.
 */
@Composable
fun CameraScreen(importing: Boolean, onCaptured: (String) -> Unit, onError: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when {
                granted -> CameraContent(importing, onCaptured, onError)
                asked -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Filled.NoPhotography,
                        "Camera permission needed",
                        "RoopAI only uses the camera while this screen is open. You can also pick a photo from the gallery instead.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Try again") }
                            Button(onClick = { context.openAppSettings() }) { Text("Open settings") }
                        }
                    }
                }
            }
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    }
}

@Composable
private fun CameraContent(importing: Boolean, onCaptured: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var front by rememberSaveable { mutableStateOf(true) }
    var capturing by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try { provider = future.get() } catch (e: Exception) { onError("The camera could not be started.") }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(provider, front) {
        val p = provider ?: return@LaunchedEffect
        try {
            p.unbindAll()
            val wanted = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val selector = if (p.hasCamera(wanted)) wanted else if (p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            p.bindToLifecycle(owner, selector, preview, capture)
        } catch (e: Exception) {
            onError("No usable camera was found on this device.")
        }
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp))
            Box(
                Modifier.size(76.dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (capturing || importing) {
                    CircularProgressIndicator(Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary)
                } else {
                    FilledIconButton(
                        onClick = {
                            capturing = true
                            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                            dir.listFiles()?.forEach { it.delete() }
                            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                            capture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        capturing = false
                                        onCaptured(android.net.Uri.fromFile(file).toString())
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        capturing = false
                                        onError("The photo could not be taken. Please try again.")
                                    }
                                },
                            )
                        },
                        modifier = Modifier.size(64.dp),
                    ) {}
                }
            }
            IconButton(onClick = { front = !front }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera", modifier = Modifier.size(30.dp))
            }
        }
    }
}
