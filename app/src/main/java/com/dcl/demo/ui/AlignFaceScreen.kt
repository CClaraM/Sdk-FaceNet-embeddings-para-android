package com.dcl.demo.ui

import android.graphics.RectF
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dcl.facesdk.FaceSdk
import com.dcl.facesdk.FaceSdkResult
import com.dcl.facesdk.detector.FaceDetectorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlin.math.max

@Composable
fun AlignFaceScreen(sdk: FaceSdk, detector: FaceDetectorEngine) {

    val scope = rememberCoroutineScope()

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var frameSize by remember { mutableStateOf(Size(0, 0)) }
    var previewSize by remember { mutableStateOf(Size(0, 0)) }
    var results by remember { mutableStateOf<List<FaceSdkResult>>(emptyList()) }

    var faceBox by remember { mutableStateOf<RectF?>(null) }
    var aligned by remember { mutableStateOf(false) }
    var stableCounter by remember { mutableStateOf(0) }

    val requiredStableFrames = 100 // ≈ 3 segundos

    Box(Modifier.fillMaxSize()) {

        // === Cámara (usa analyzeFrame directo del SDK)
        CameraXPreviewAnalyzer(
            modifier = Modifier.fillMaxSize(),
            onPreviewReady = { view ->
                previewView = view
                view.post {
                    previewSize = Size(view.width, view.height)
                    Log.d("AlignFaceScreen", "📱 PreviewView = ${view.width}x${view.height}")
                }
            },
            onFrame = { image: ImageProxy ->
                val rotation = image.imageInfo.rotationDegrees
                val width = if (rotation % 180 == 0) image.width else image.height
                val height = if (rotation % 180 == 0) image.height else image.width
                frameSize = Size(width, height)

                scope.launch(Dispatchers.Default) {
                    try {
                        val res = sdk.analyzeFrame(
                            image = image,
                            isFrontCamera = true,
                            doSpoof = false,
                            doEmbedding = false
                        )
                        withContext(Dispatchers.Main) {
                            results = res
                            faceBox = res.firstOrNull()?.bbox?.let { RectF(it) } // ✅ conversión segura
                        }
                    } catch (e: Exception) {
                        Log.e("AlignFaceScreen", "❌ analyzeFrame error: ${e.message}")
                    } finally {
                        image.close()
                    }
                }
            }
        )

        // === Overlay dinámico (usa frameSize + previewSize)
        AlignFaceOverlay(
            faceBox = faceBox,
            frameSize = frameSize,
            previewSize = previewSize,
            isFrontCamera = true,
            stableCounter = stableCounter,
            requiredStableFrames = requiredStableFrames,
            guideOffsetYRatio = 0f,
            modifier = Modifier.fillMaxSize(),
            onAlignedChange = { aligned = it }
        )

        // === Control del conteo y progreso
        LaunchedEffect(Unit) {
            while (true) {
                if (aligned) {
                    stableCounter += 2
                } else {
                    stableCounter = max(0, stableCounter - 3)
                }

                stableCounter = stableCounter.coerceIn(0, requiredStableFrames)

                if (stableCounter >= requiredStableFrames) {
                    Log.d("AlignFaceScreen", "✅ Rostro alineado y estable — continuar flujo")
                    stableCounter = 0 // 🔁 permite repetir el ciclo
                }

                kotlinx.coroutines.delay(25L)
            }
        }

        // ⚠️ 👉 Aquí al final del Composable, añade el DisposableEffect
        DisposableEffect(Unit) {
            onDispose {
                try {
                    scope.cancel()     // 🔹 cancela corrutinas en curso
                    sdk.close()        // 🔹 libera intérpretes y bitmap
                    Log.d("IdentifyLive", "🧹 Recursos liberados correctamente")
                } catch (e: Exception) {
                    Log.e("IdentifyLive", "❌ Error al liberar: ${e.message}")
                }
            }
        }
    }
}
