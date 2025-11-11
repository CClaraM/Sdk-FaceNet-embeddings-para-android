package com.dcl.demo.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dcl.demo.ui.LiveTag
import kotlin.math.max

/**
 * 🎯 FaceTagsOverlay
 *
 * Dibuja las etiquetas (recuadro + texto) sobre los rostros detectados.
 * - Se adapta automáticamente al tamaño del frame.
 * - Cambia el color según el estado del rostro:
 *   🟢 Vivo / autenticado
 *   🔴 Spoof o no vivo
 *   🟡 Desconocido
 *
 * @param tags Lista de etiquetas activas (una por rostro).
 * @param frameSize Tamaño del frame de cámara (ancho/alto en píxeles).
 */
@Composable
fun FaceTagsOverlay(
    tags: List<LiveTag>,
    frameSize: android.util.Size,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        if (frameSize.width == 0 || frameSize.height == 0) return@Canvas

        val scaleX = size.width / frameSize.width
        val scaleY = size.height / frameSize.height

        for (tag in tags) {
            drawFaceTag(tag, scaleX, scaleY, textMeasurer)
        }
    }
}

/* ===================== Funciones de dibujo ===================== */

private fun DrawScope.drawFaceTag(
    tag: LiveTag,
    scaleX: Float,
    scaleY: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val rect = tag.rect
    val left = rect.left * scaleX
    val top = rect.top * scaleY
    val width = rect.width() * scaleX
    val height = rect.height() * scaleY

    // 🎨 Colores según estado
    val (boxColor, textColor) = when {
        !tag.isLive -> Color.Red to Color.Red
        tag.text?.contains("Desconocido", ignoreCase = true) == true -> Color.Yellow to Color.Yellow
        else -> Color(0xFF00FF7F) to Color(0xFF00FF7F) // Verde claro (autenticado)
    }

    // 🔲 Recuadro facial
    drawRoundRect(
        color = boxColor,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 3.dp.toPx())
    )

    // 🏷 Texto de etiqueta
    val label = tag.text ?: "Desconocido"
    val fontSize = max(14.sp.value, width / 20f).sp
    val textLayout = textMeasurer.measure(
        text = label,
        style = TextStyle(
            color = textColor,
            fontSize = fontSize,
        )
    )

    // Fondo semitransparente detrás del texto
    val padding = 6.dp.toPx()
    val bgWidth = textLayout.size.width + padding * 2
    val bgHeight = textLayout.size.height + padding
    val bgLeft = left
    val bgTop = (top - bgHeight).coerceAtLeast(0f)

    drawRoundRect(
        color = Color(0x66000000), // fondo negro translúcido
        topLeft = Offset(bgLeft, bgTop),
        size = Size(bgWidth, bgHeight),
        cornerRadius = CornerRadius(8f, 8f)
    )

    drawText(
        textLayout,
        topLeft = Offset(bgLeft + padding, bgTop + padding / 2)
    )
}
