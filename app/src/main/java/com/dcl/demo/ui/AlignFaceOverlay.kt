package com.dcl.demo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ================================================================
   🔹 Overlay con tolerancia y progreso visual
   ================================================================ */
@Composable
fun AlignFaceOverlay(
    faceBox: android.graphics.RectF?,
    frameSize: android.util.Size,
    previewSize: android.util.Size,
    isFrontCamera: Boolean = true,
    stableCounter: Int,
    requiredStableFrames: Int,
    guideOffsetYRatio: Float = 0f,
    modifier: Modifier = Modifier,
    minSizeRatio: Float = 0.29f,
    maxSizeRatio: Float = 0.80f,
    tolerancePx: Float = 40f,
    onAlignedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val viewW = size.width
        val viewH = size.height
        val previewW = previewSize.width.toFloat().coerceAtLeast(1f)
        val previewH = previewSize.height.toFloat().coerceAtLeast(1f)

        // === Marco guía
        val overlayWidth = viewW * 0.60f
        val overlayHeight = overlayWidth * 1.15f
        val left = (viewW - overlayWidth) / 2f
        val top = (viewH - overlayHeight) / 2f + (guideOffsetYRatio * viewH)
        val guideRect = android.graphics.RectF(left, top, left + overlayWidth, top + overlayHeight)

        var color = Color.Red
        var alignedNow = false

        faceBox?.let { box ->
            if (frameSize.width > 0 && frameSize.height > 0) {
                val frameW = frameSize.width.toFloat()
                val frameH = frameSize.height.toFloat()

                val scale = maxOf(previewW / frameW, previewH / frameH)
                val dx = (previewW - frameW * scale) / 2f
                val dy = (previewH - frameH * scale) / 2f

                val mapped = android.graphics.RectF(
                    (box.left * scale + dx) * (viewW / previewW),
                    (box.top * scale + dy) * (viewH / previewH),
                    (box.right * scale + dx) * (viewW / previewW),
                    (box.bottom * scale + dy) * (viewH / previewH)
                )

                val expandedGuide = android.graphics.RectF(
                    guideRect.left - tolerancePx,
                    guideRect.top - tolerancePx,
                    guideRect.right + tolerancePx,
                    guideRect.bottom + tolerancePx
                )

                val faceHeightRatio = mapped.height() / viewH
                val isInside = expandedGuide.contains(mapped)
                val isSizeOk = faceHeightRatio in minSizeRatio..maxSizeRatio
                alignedNow = isInside && isSizeOk

                color = when {
                    alignedNow -> Color.Green
                    isInside -> Color.Yellow
                    else -> Color.Red
                }

                drawRect(
                    color = Color.Yellow,
                    topLeft = Offset(mapped.left, mapped.top),
                    size = androidx.compose.ui.geometry.Size(mapped.width(), mapped.height()),
                    style = Stroke(width = 2f)
                )
            }
        }

        onAlignedChange(alignedNow)

        drawRect(
            color = color,
            topLeft = Offset(guideRect.left, guideRect.top),
            size = androidx.compose.ui.geometry.Size(guideRect.width(), guideRect.height()),
            style = Stroke(width = 5.dp.toPx())
        )

        // === Texto de progreso
        val progress = stableCounter.toFloat() / requiredStableFrames
        val percent = (progress * 100).toInt().coerceIn(0, 100)

        drawContext.canvas.nativeCanvas.apply {
            drawText(
                if (percent >= 100) "Rostro alineado ✅"
                else "Mantén la posición... $percent%",
                viewW / 2f,
                guideRect.top - 16.dp.toPx(),
                android.graphics.Paint().apply {
                    color = Color.White
                    textSize = 22.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }
    }
}