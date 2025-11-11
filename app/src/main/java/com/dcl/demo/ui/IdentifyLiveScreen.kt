package com.dcl.demo.ui

import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dcl.demo.data.AppDatabase
import com.dcl.demo.data.model.UserData
import com.dcl.facesdk.FaceSdk
import com.dcl.facesdk.utils.toFloatArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import com.dcl.facesdk.FaceSdkResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 🧠 IdentifyLiveScreen v2
 *
 * Detecta múltiples rostros en tiempo real, verifica spoof y genera embeddings
 * para compararlos contra la base de datos local. Muestra en pantalla el nombre
 * del usuario o "Desconocido" sobre cada rostro detectado.
 *
 * - Usa sdk.analyzeFrame() para detección y spoof.
 * - Usa sdk.getEmbedding() solo cuando el rostro se mueve o cumple el cooldown.
 * - No recalcula en cada frame (usa shouldComputeEmbedding()).
 * - Totalmente compatible con Compose y CameraXPreviewAnalyzer.
 */
@ExperimentalGetImage
@Composable
fun IdentifyLiveScreen(
    sdk: FaceSdk,
    matchThreshold: Float = 0.75f,
    maxFacesPerFrame: Int = 6,
    embedCooldownMs: Long = 400L,
    labelPersistMs: Long = 1500L,
    labelGraceMs: Long = 700L,
    movementCenterPx: Float = 8f,
    movementScaleFrac: Float = 0.12f
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var frameSize by remember { mutableStateOf(Size(0, 0)) }

    // Cargar embeddings desde base de datos local
    val users = remember { mutableStateListOf<UserData>() }
    val dbEmbeddings = remember { mutableStateListOf<FloatArray>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(ctx).userDao()
            val list = dao.getAllUsers()
            withContext(Dispatchers.Main) {
                users.clear(); users += list
                dbEmbeddings.clear(); dbEmbeddings += list.map { it.embedding.toFloatArray() }
                Log.d("IdentifyLive", "Base de datos cargada (${users.size} usuarios)")
            }
        }
    }

    val faceTracks = remember { mutableStateListOf<FaceTrack>() }
    val tags = remember { mutableStateListOf<LiveTag>() }
    var frameCounter by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize()) {

        // Cámara con analizador de frames
        CameraXPreviewAnalyzer(
            modifier = Modifier.fillMaxSize(),
            onPreviewReady = { previewView = it },
            onFrame = { image: ImageProxy ->
                val rot = image.imageInfo.rotationDegrees
                val srcW = if (rot % 180 == 0) image.width else image.height
                val srcH = if (rot % 180 == 0) image.height else image.width
                frameSize = Size(srcW, srcH)

                scope.launch(Dispatchers.Default) {
                    try {
                        val results: List<FaceSdkResult> = sdk.analyzeFrame(
                            image = image,
                            isFrontCamera = true,
                            doSpoof = true,
                            doEmbedding = false // solo spoof/detección
                        )

                        val now = System.currentTimeMillis()
                        val limited = results.take(min(results.size, maxFacesPerFrame))

                        // Emparejar detecciones con tracks previos (seguimiento)
                        val updatedTracks = mutableListOf<FaceTrack>()
                        val usedPrev = BooleanArray(faceTracks.size) { false }

                        for (det in limited) {
                            val live = det.spoof?.isLive == true
                            val matchIndex = matchToExistingTrack(det.bbox, faceTracks)

                            val prev: FaceTrack? =
                                if (matchIndex >= 0 && !usedPrev[matchIndex]) {
                                    usedPrev[matchIndex] = true
                                    faceTracks[matchIndex]
                                } else null

                            val track = if (prev == null) {
                                FaceTrack(
                                    bboxMirror = det.bbox,
                                    lastSeen = now,
                                    name = null,
                                    score = 0f,
                                    isLive = live,
                                    lockUntil = 0L,
                                    lastEmbedAt = 0L,
                                    lastEmbedRectMirror = det.bbox
                                )
                            } else {
                                prev.copy(
                                    bboxMirror = det.bbox,
                                    lastSeen = now,
                                    isLive = live
                                )
                            }
                            updatedTracks += track
                        }

                        // Procesar embeddings solo si hay usuarios en DB
                        if (users.isNotEmpty()) {
                            updatedTracks.forEachIndexed { idx, t ->
                                val mustCompute = shouldComputeEmbedding(
                                    t = t,
                                    now = now,
                                    frameCounter = frameCounter,
                                    perTrackOffset = idx,
                                    cooldownMs = embedCooldownMs,
                                    movementCenterPx = movementCenterPx,
                                    movementScaleFrac = movementScaleFrac
                                )

                                if (t.isLive && mustCompute && now > t.lockUntil) {
                                    try {
                                        // 🧬 Generar embedding con pipeline actual
                                        val emb = sdk.getEmbedding(
                                            frameBitmap = sdk.lastFrameBitmap ?: return@forEachIndexed,
                                            bboxMirror = t.bboxMirror
                                        )
                                        val res = sdk.identify(
                                            emb,
                                            dbEmbeddings,
                                            threshold = matchThreshold
                                        )
                                        if (res.matched) {
                                            val u = users[res.bestIndex!!]
                                            t.name = u.name
                                            t.score = res.score
                                            t.lockUntil = now + labelPersistMs
                                        } else {
                                            if (now < t.lockUntil) {
                                                // mantener etiqueta anterior
                                            } else {
                                                t.name = "Desconocido"
                                                t.score = 0f
                                            }
                                        }
                                        t.lastEmbedAt = now
                                        t.lastEmbedRectMirror = t.bboxMirror
                                    } catch (e: Exception) {
                                        Log.e("IdentifyLive", "❌ Error embedding: ${e.message}")
                                    }
                                } else {
                                    // Mantener etiquetas activas o limpiar spoof
                                    if (!t.isLive) {
                                        t.name = "Desconocido"
                                        t.score = 0f
                                        t.lockUntil = 0L
                                    } else if (t.name != null && t.name != "Desconocido" && now > t.lockUntil && now - t.lastEmbedAt < labelGraceMs) {
                                        t.lockUntil = now + (labelGraceMs - (now - t.lastEmbedAt))
                                    }
                                }
                            }
                        }

                        // Filtrar tracks inactivos
                        val expireMs = 1000L.coerceAtLeast(labelPersistMs)
                        val pruned = updatedTracks.filter { now - it.lastSeen <= expireMs }
                        faceTracks.clear(); faceTracks.addAll(pruned)

                        // Actualizar etiquetas visuales
                        val tagList = faceTracks.map {
                            LiveTag(
                                rect = RectF(it.bboxMirror),
                                text = if (it.name != null && it.name != "Desconocido" && it.score > 0f)
                                    "${it.name} (%.2f)".format(it.score)
                                else it.name ?: "Desconocido",
                                isLive = it.isLive
                            )
                        }

                        withContext(Dispatchers.Main) {
                            tags.replaceAll(tagList)
                        }

                    } catch (e: Exception) {
                        Log.e("IdentifyLive", "❌ Error detectando rostros: ${e.message}")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        image.close()
                    }
                }

                frameCounter++
            }
        )

        // Overlay visual
        FaceTagsOverlay(
            tags = tags,
            frameSize = frameSize,
            modifier = Modifier.fillMaxSize()
        )

        if (users.isEmpty()) {
            Text(
                text = "No hay usuarios registrados",
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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

/* =================== MODELOS / HELPERS =================== */

data class FaceTrack(
    var bboxMirror: Rect,
    var lastSeen: Long,
    var name: String?,
    var score: Float,
    var isLive: Boolean,
    var lockUntil: Long,
    var lastEmbedAt: Long,
    var lastEmbedRectMirror: Rect
)

data class LiveTag(
    val rect: RectF,
    val text: String,
    val isLive: Boolean
)

private fun <T> SnapshotStateList<T>.replaceAll(newItems: List<T>) {
    clear(); addAll(newItems)
}

/* ======= Emparejamiento de detecciones y control de movimiento ======= */

private fun iou(a: Rect, b: Rect): Float {
    val interLeft = max(a.left, b.left)
    val interTop = max(a.top, b.top)
    val interRight = min(a.right, b.right)
    val interBottom = min(a.bottom, b.bottom)
    val interW = (interRight - interLeft).coerceAtLeast(0)
    val interH = (interBottom - interTop).coerceAtLeast(0)
    val inter = interW * interH
    val areaA = (a.width()).coerceAtLeast(0) * (a.height()).coerceAtLeast(0)
    val areaB = (b.width()).coerceAtLeast(0) * (b.height()).coerceAtLeast(0)
    val union = areaA + areaB - inter
    return if (union <= 0) 0f else inter.toFloat() / union.toFloat()
}

private fun centerDist2(a: Rect, b: Rect): Float {
    val ax = (a.left + a.right) / 2f
    val ay = (a.top + a.bottom) / 2f
    val bx = (b.left + b.right) / 2f
    val by = (b.top + b.bottom) / 2f
    val dx = ax - bx
    val dy = ay - by
    return dx * dx + dy * dy
}

private fun matchToExistingTrack(bboxMirror: Rect, tracks: List<FaceTrack>): Int {
    var best = -1
    var bestIoU = 0f
    tracks.forEachIndexed { idx, t ->
        val i = iou(bboxMirror, t.bboxMirror)
        if (i > bestIoU) { bestIoU = i; best = idx }
    }
    if (bestIoU >= 0.3f) return best

    var bestIdx = -1
    var bestD2 = Float.MAX_VALUE
    tracks.forEachIndexed { idx, t ->
        val d2 = centerDist2(bboxMirror, t.bboxMirror)
        if (d2 < bestD2) { bestD2 = d2; bestIdx = idx }
    }
    return if (bestD2 < 2000f) bestIdx else -1
}

private fun shouldComputeEmbedding(
    t: FaceTrack,
    now: Long,
    frameCounter: Int,
    perTrackOffset: Int,
    cooldownMs: Long,
    movementCenterPx: Float,
    movementScaleFrac: Float
): Boolean {
    if (now - t.lastEmbedAt < cooldownMs) return false
    if (((frameCounter + perTrackOffset) % 2) != 0) return false

    val prev = t.lastEmbedRectMirror
    val cur = t.bboxMirror

    val prevCx = (prev.left + prev.right) / 2f
    val prevCy = (prev.top + prev.bottom) / 2f
    val curCx = (cur.left + cur.right) / 2f
    val curCy = (cur.top + cur.bottom) / 2f

    val dCenter = sqrt((prevCx - curCx) * (prevCx - curCx) + (prevCy - curCy) * (prevCy - curCy))
    if (dCenter >= movementCenterPx) return true

    val prevDiag = sqrt((prev.width() * prev.width() + prev.height() * prev.height()).toFloat())
    val curDiag = sqrt((cur.width() * cur.width() + cur.height() * cur.height()).toFloat())
    val scaleDelta = abs(curDiag - prevDiag) / (prevDiag.coerceAtLeast(1f))
    return scaleDelta >= movementScaleFrac
}
