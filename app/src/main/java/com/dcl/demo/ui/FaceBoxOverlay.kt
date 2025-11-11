package com.dcl.demo.ui

import android.graphics.Paint
import android.graphics.RectF
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.dcl.facesdk.FaceSdkResult
import kotlin.math.max

@Composable
fun FaceBoxOverlay(
    results: List<FaceSdkResult>,
    frameSize: Size,         // Tamaño del frame analizado (image.width / height)
    previewSize: Size,       // Tamaño del PreviewView actual en pantalla
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    if (frameSize.width == 0 || frameSize.height == 0) return

    val textSizePx = with(LocalDensity.current) { 14.sp.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {

        // === Medidas de PreviewView en Compose
        val viewW = size.width
        val viewH = size.height
        val frameW = frameSize.width.toFloat()
        val frameH = frameSize.height.toFloat()
        val previewW = previewSize.width.toFloat().coerceAtLeast(1f)
        val previewH = previewSize.height.toFloat().coerceAtLeast(1f)

        // === Escalado y centrado igual que CameraXPreview (FILL_CENTER)
        val scale = max(previewW / frameW, previewH / frameH)
        val dx = (previewW - frameW * scale) / 2f
        val dy = (previewH - frameH * scale) / 2f

        results.forEach { r ->
            val b = r.bbox
            if (b == null) return@forEach

            // 🔹 Mapeo del bounding box al Canvas visible
            val mapped = RectF(
                (b.left * scale + dx) * (viewW / previewW),
                (b.top * scale + dy) * (viewH / previewH),
                (b.right * scale + dx) * (viewW / previewW),
                (b.bottom * scale + dy) * (viewH / previewH)
            )

            // === Color del recuadro según spoof
            val spoof = r.spoof
            val isLive = spoof?.isLive == true
            val liveScore = spoof?.liveScore ?: 0f

            // 🔹 Color dinámico según score
            val boxColor = when {
                spoof == null -> Color.Gray
                isLive -> Color.Green
                else -> {
                    val danger = (1f - liveScore).coerceIn(0f, 1f)
                    Color(red = danger, green = liveScore, blue = 0f)
                }
            }

            val paintBox = Paint().apply {
                color = android.graphics.Color.argb(255,
                    (boxColor.red * 255).toInt(),
                    (boxColor.green * 255).toInt(),
                    (boxColor.blue * 255).toInt()
                )
                style = Paint.Style.STROKE
                strokeWidth = 5f
                isAntiAlias = true
            }

            // === Dibuja el rectángulo del rostro
            drawContext.canvas.nativeCanvas.drawRect(mapped, paintBox)

            // === Texto informativo
            val paintText = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = textSizePx
                isAntiAlias = true
            }

            val text = when {
                spoof == null -> "Detectando..."
                isLive -> "✅ LIVE  %.2f".format(liveScore)
                else -> "❌ SPOOF %.2f".format(liveScore)
            }

            drawContext.canvas.nativeCanvas.drawText(
                text,
                mapped.left + 4f,
                mapped.top - 8f,
                paintText
            )
        }
    }
}
