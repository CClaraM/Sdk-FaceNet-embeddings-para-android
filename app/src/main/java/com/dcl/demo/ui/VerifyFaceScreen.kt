package com.dcl.demo.ui

import android.widget.Toast
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dcl.demo.data.AppDatabase
import com.dcl.demo.utils.captureHDPhoto
import com.dcl.facesdk.FaceSdk
import com.dcl.facesdk.detector.FaceDetectorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dcl.facesdk.utils.toFloatArray
import kotlinx.coroutines.CoroutineScope
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import android.graphics.Bitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.cancel
import android.util.Log

@ExperimentalGetImage
@Composable
fun VerifyFaceScreen(
    sdk: FaceSdk,
    detector: FaceDetectorEngine,
    matchThreshold: Float = 0.75f,
    requiredStableFrames: Int = 60
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    var faceBox by remember { mutableStateOf<android.graphics.RectF?>(null) }
    var frameSize by remember { mutableStateOf(android.util.Size(0, 0)) }
    var previewSize by remember { mutableStateOf(android.util.Size(0, 0)) }

    var aligned by remember { mutableStateOf(false) }
    var stableCounter by remember { mutableStateOf(0) }

    var verifying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    var liveCounter by remember { mutableStateOf(0) }
    var spoofCounter by remember { mutableStateOf(0) }
    var spoofDetected by remember { mutableStateOf(false) }


    Box(Modifier.fillMaxSize()) {

        CameraXPreviewAnalyzer(
            modifier = Modifier.fillMaxSize(),
            onPreviewReady = { view ->
                previewView = view
                // 📏 medir tamaño real del PreviewView
                view.post {
                    previewSize = android.util.Size(view.width, view.height)
                    Log.d("PreviewSize", "PreviewView = ${view.width}x${view.height}")
                }
            },
            onImageCaptureReady = { imageCapture = it },
            onFrame = { image: ImageProxy ->
                val rot = image.imageInfo.rotationDegrees
                val srcW = if (rot % 180 == 0) image.width else image.height
                val srcH = if (rot % 180 == 0) image.height else image.width
                frameSize = android.util.Size(srcW, srcH)

                scope.launch(Dispatchers.Default) {
                    val results = sdk.analyzeFrame(
                        image,
                        isFrontCamera = true,
                        doSpoof = true,
                        doEmbedding = false
                    )
                    val first = results.firstOrNull()
                    withContext(Dispatchers.Main) {
                        faceBox = first?.bbox?.let { android.graphics.RectF(it) }
                        // 🔹 Análisis del spoof
                        val isLive = first?.spoof?.isLive ?: false
                        if (isLive) liveCounter++ else spoofCounter++

                        // 🔹 Control de falsos positivos / decisión
                        val total = liveCounter + spoofCounter
                        if (total > 30) { // necesita cierto número de frames antes de decidir
                            spoofDetected = spoofCounter > liveCounter * 1.5f
                            // Reinicia contadores para no acumular infinito
                            liveCounter = 0
                            spoofCounter = 0
                        }
                    }
                }
            }
        )

        AlignFaceOverlay(
            faceBox = faceBox,
            frameSize = frameSize,
            previewSize = previewSize,
            isFrontCamera = true,
            stableCounter = stableCounter,
            requiredStableFrames = requiredStableFrames,
            guideOffsetYRatio = 0f,
            modifier = Modifier.fillMaxSize(),
            tolerancePx = 50f,
            onAlignedChange = { aligned = it }
        )

        // 🔹 Indicador de progreso (texto simple)
        Text(
            text = "${(progress * 100).toInt()}%",
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
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

    // 🔁 Contador estable + acción de verificación
    LaunchedEffect(aligned) {
        while (true) {
            if (aligned) {
                stableCounter += 2
            } else {
                stableCounter = maxOf(0, stableCounter - 3)
            }

            stableCounter = stableCounter.coerceIn(0, requiredStableFrames)
            progress = stableCounter / requiredStableFrames.toFloat()

            if (spoofDetected) {
                if (stableCounter >= requiredStableFrames && !verifying) {
                    stableCounter = 0
                    progress = 0f
                    verifying = false
                    Log.w("SpoofGuard", "❌ Spoof detectado: bloqueo temporal del conteo")
                    Toast.makeText(ctx, "❌ Rostro falso detectado", Toast.LENGTH_LONG).show()
                }
            } else {
                // 🚀 Ejecutar cuando llega a 100 %
                if (stableCounter >= requiredStableFrames && !verifying) {
                    stableCounter = 0
                    verifying = true
                    launchVerify(ctx, imageCapture, sdk,matchThreshold) {
                        verifying = false
                    }
                }
            }



            delay(50)
        }
    }
}

/* ================================================================
   🔹 Acción de verificación
   ================================================================ */
fun CoroutineScope.launchVerify(
    ctx: Context,
    imageCapture: ImageCapture?,
    sdk: FaceSdk,
    matchThreshold: Float,
    onFinish: () -> Unit
) {

    try {


        val cap = imageCapture ?: run {
            Log.e("VerifyFlow", "❌ ImageCapture es nulo, no se puede continuar")
            return
        }

        Log.d("VerifyFlow", "🚀 Iniciando captura HD para verificación")

        // 1️⃣ Captura de imagen en alta resolución
        captureHDPhoto(cap) { fullRes: Bitmap ->
            // Lanza una nueva coroutine dentro del callback
            this.launch(Dispatchers.Default) {
                try {
                    Log.d("VerifyFlow", "🧠 Procesando foto HD...")

                    val result = sdk.processPhoto(fullRes)
                    if (result == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, "⚠️ No se detectó rostro", Toast.LENGTH_LONG).show()
                            onFinish()
                        }
                        return@launch
                    }

                    val emb = result.embedding
                    if (emb == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, "❌ Error al generar embedding", Toast.LENGTH_LONG).show()
                            onFinish()
                        }
                        return@launch
                    }

                    // 📦 Consultar usuarios en Room
                    val users = AppDatabase.getInstance(ctx).userDao().getAllUsers()

                    if (users.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, "⚠️ No hay usuarios registrados", Toast.LENGTH_LONG).show()
                            onFinish()
                        }
                        return@launch
                    }

                    val dbEmbeddings = users.map { it.embedding.toFloatArray() }
                    val match = sdk.identify(emb, dbEmbeddings, threshold = matchThreshold)

                    withContext(Dispatchers.Main) {
                        if (match.matched && match.bestIndex != null) {
                            val user = users[match.bestIndex!!]
                            Toast.makeText(ctx, "✅ Bienvenido ${user.name}", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(ctx, "❌ Usuario no reconocido", Toast.LENGTH_LONG).show()
                        }
                        onFinish()
                    }
                } catch (e: Exception) {
                    Log.e("VerifyFlow", "❌ Error en verificación: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "❌ Error en verificación", Toast.LENGTH_LONG).show()
                        onFinish()
                    }
                }
            }
        }


    } catch (e: Exception) {
        Log.e("VerifyFlow", "❌ Error general en launchVerify: ${e.message}", e)
    }
}
