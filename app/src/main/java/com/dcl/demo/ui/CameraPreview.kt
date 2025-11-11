package com.dcl.demo.ui

import android.util.Size
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dcl.facesdk.FaceSdk
import com.dcl.facesdk.FaceSdkResult
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel

@Composable
fun CameraPreview(
    sdk: FaceSdk,
    isFrontCamera: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var frameSize by remember { mutableStateOf(Size(0, 0)) }
    var previewSize by remember { mutableStateOf(Size(0, 0)) }
    var results by remember { mutableStateOf<List<FaceSdkResult>>(emptyList()) }

    Box(modifier = modifier.fillMaxSize()) {

        CameraXPreviewAnalyzer(
            modifier = Modifier.fillMaxSize(),
            onPreviewReady = { view ->
                previewView = view
                // Medir tamaño real del PreviewView
                view.post {
                    previewSize = Size(view.width, view.height)
                    Log.d("PreviewSize", "PreviewView = ${view.width}x${view.height}")
                }
            },
            onFrame = { image: ImageProxy ->
                val rot = image.imageInfo.rotationDegrees
                val srcW = if (rot % 180 == 0) image.width else image.height
                val srcH = if (rot % 180 == 0) image.height else image.width
                frameSize = Size(srcW, srcH)

                scope.launch(Dispatchers.Default) {
                    try {
                        // 🔹 Análisis directo del frame (sin convertir a bitmap)
                        val res = sdk.analyzeFrame(
                            image = image,
                            isFrontCamera = isFrontCamera,
                            doSpoof = true,
                            doEmbedding = false
                        )
                        withContext(Dispatchers.Main) { results = res }
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "❌ Error en analyzeFrame: ${e.message}")
                    } finally {
                        image.close()
                    }
                }
            }
        )

        // 🔹 Overlay dinámico que muestra las cajas y scores
        FaceBoxOverlay(
            results = results,
            frameSize = frameSize,
            previewSize = previewSize,
            isFrontCamera = isFrontCamera,
            modifier = Modifier.fillMaxSize()
        )

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


