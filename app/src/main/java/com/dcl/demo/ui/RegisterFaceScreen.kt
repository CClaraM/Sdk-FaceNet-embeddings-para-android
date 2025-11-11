package com.dcl.demo.ui

import android.graphics.RectF
import android.util.Size
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
import com.dcl.demo.data.model.UserData
import com.dcl.demo.utils.captureHDPhoto
import com.dcl.facesdk.FaceSdk
import com.dcl.facesdk.detector.FaceDetectorEngine
import com.dcl.facesdk.utils.toBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlin.io.encoding.ExperimentalEncodingApi

import android.util.Log

@ExperimentalEncodingApi
@ExperimentalGetImage
@Composable
fun RegisterFaceScreen(
    sdk: FaceSdk,
    detector: FaceDetectorEngine
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    var faceBox by remember { mutableStateOf<RectF?>(null) }
    var frameSize by remember { mutableStateOf(Size(0, 0)) }
    var previewSize by remember { mutableStateOf(Size(0, 0)) } // 🔥 tamaño real del PreviewView

    var aligned by remember { mutableStateOf(false) }
    var stableCounter by remember { mutableStateOf(0) }
    val requiredStableFrames = 60 // ~3 s

    var verifying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    var pendingEmbedding by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    var liveCounter by remember { mutableStateOf(0) }
    var spoofCounter by remember { mutableStateOf(0) }
    var spoofDetected by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {

        CameraXPreviewAnalyzer(
            modifier = Modifier.fillMaxSize(),
            onPreviewReady = { view ->
                previewView = view
                // 🔹 medir tamaño real del PreviewView en pantalla
                view.post {
                    previewSize = Size(view.width, view.height)
                    Log.d("PreviewSize", "PreviewView real = ${view.width}x${view.height}")
                }
            },
            onImageCaptureReady = { imageCapture = it },
            onFrame = { image: ImageProxy ->
                val rot = image.imageInfo.rotationDegrees
                val srcW = if (rot % 180 == 0) image.width else image.height
                val srcH = if (rot % 180 == 0) image.height else image.width
                frameSize = Size(srcW, srcH) // 🔹 tamaño real del frame (rotado)

                scope.launch(Dispatchers.Default) {
                    val results = sdk.analyzeFrame(
                        image,
                        isFrontCamera = true,
                        doSpoof = true,
                        doEmbedding = false
                    )
                    val first = results.firstOrNull()
                    withContext(Dispatchers.Main) {
                        faceBox = first?.bbox?.let { RectF(it) }

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
            previewSize = previewSize, // 🔥 ahora recibe tamaño real del preview
            isFrontCamera = true,
            stableCounter = stableCounter,
            requiredStableFrames = requiredStableFrames,
            guideOffsetYRatio = 0f,
            modifier = Modifier.fillMaxSize(),
            onAlignedChange = { aligned = it },
            tolerancePx = 50f
        )

        Text(
            text = "${(stableCounter.toFloat() / requiredStableFrames * 100).toInt()}%",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(Modifier)
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

    LaunchedEffect(Unit) {
        while (true) {
            if (aligned && !verifying) {
                stableCounter += 2 // suma rápido mientras está centrado
            } else if (!verifying) {
                stableCounter = maxOf(0, stableCounter - 3) // decae lentamente
            }

            // Normalizamos a 0–100 %
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
                // 🔹 Cuando llega al 100 %
                if (stableCounter >= requiredStableFrames && !verifying) {
                    verifying = true
                    stableCounter = 0
                    Log.d("RegisterFlow", "✅ Rostro alineado: iniciando captura HD...")

                    val cap = imageCapture
                    if (cap != null) {
                        captureHDPhoto(cap) { fullRes ->
                            scope.launch(Dispatchers.Default) {
                                val result = sdk.processPhoto(fullRes)
                                withContext(Dispatchers.Main) {
                                    verifying = false // 🔁 permitir repetir luego
                                }

                                if (result?.embedding != null) {
                                    val embedding = result.embedding
                                    if (embedding != null) {
                                        withContext(Dispatchers.Main) {
                                            pendingEmbedding = embedding.toBase64()
                                            showSaveDialog = true
                                        }
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            ctx,
                                            "❌ Rostro no válido o spoof",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    } else {
                        verifying = false
                    }
                }
            }


            delay(50) // controla velocidad de actualización (~20 fps)
        }
    }





    if (showSaveDialog && pendingEmbedding != null) {
        SaveEmbeddingDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { userId, name ->
                scope.launch(Dispatchers.IO) {
                    AppDatabase.getInstance(ctx).userDao().insert(
                        UserData(
                            userId = userId.toLong(),
                            name = name,
                            role_Id = 1,
                            embedding = pendingEmbedding!!,
                            fingerprintCsv = ""
                        )
                    )
                }
                showSaveDialog = false
                Toast.makeText(ctx, "Usuario registrado ✅", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
