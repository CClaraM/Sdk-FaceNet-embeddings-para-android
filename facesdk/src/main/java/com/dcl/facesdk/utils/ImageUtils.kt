package com.dcl.facesdk.utils

import android.util.Log
import androidx.camera.core.ImageProxy
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix

import android.graphics.Rect

import kotlin.math.sqrt
import androidx.exifinterface.media.ExifInterface

fun imageProxyToBitmapRGBA(image: ImageProxy): Bitmap {
    val plane = image.planes[0].buffer
    val width = image.width
    val height = image.height

    // ✅ MediaPipe necesita ARGB_8888
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(plane)

    // Rotación
    val rot = image.imageInfo.rotationDegrees
    val m = Matrix().apply { postRotate(rot.toFloat()) }
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)

    Log.d("toBitmapProcessed", "Bitmap listo: ${rotated.width}x${rotated.height}, rot=$rot")
    return rotated // ⚠️ Mantener ARGB_8888 para MediaPipe
}

fun Bitmap.prepareForEmbedding(): Bitmap {
    // ✅ FaceNet requiere ARGB_8888 (igual que MediaPipe)
    return if (this.config != Bitmap.Config.ARGB_8888) {
        this.copy(Bitmap.Config.ARGB_8888, false)
    } else this
}


// Expande y cuadra el rectángulo
fun expandSquare(rect: android.graphics.Rect, padFrac: Float, imgW: Int, imgH: Int): android.graphics.Rect {
    val cx = (rect.left + rect.right) / 2f
    val cy = (rect.top + rect.bottom) / 2f
    val size = maxOf(rect.width(), rect.height()).toFloat()
    val newSize = (size * (1f + padFrac)).toInt()

    val half = newSize / 2
    var left = (cx - half).toInt()
    var top = (cy - half).toInt()
    var right = left + newSize
    var bottom = top + newSize

    // clamp
    left = left.coerceIn(0, imgW - 1)
    top = top.coerceIn(0, imgH - 1)
    right = right.coerceIn(left + 1, imgW)
    bottom = bottom.coerceIn(top + 1, imgH)

    return android.graphics.Rect(left, top, right, bottom)
}

fun safeCropWithMargin(src: Bitmap, rect: android.graphics.Rect, padFrac: Float = 0.15f): Bitmap? {
    val er = expandSquare(rect, padFrac, src.width, src.height)
    val w = er.width().coerceAtLeast(1).coerceAtMost(src.width - er.left)
    val h = er.height().coerceAtLeast(1).coerceAtMost(src.height - er.top)
    return try { Bitmap.createBitmap(src, er.left, er.top, w, h) } catch (_: Exception) { null }
}


// ROTATE a partir de grados
fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return src
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}


// Decodifica archivo respetando EXIF orientation (opcional si trabajas con path)
fun decodeFileRespectExif(path: String): Bitmap {
    val bm = BitmapFactory.decodeFile(path)
    val exif = ExifInterface(path)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    return rotateBitmap(bm, rotation)
}

fun mirroredToNonMirrored(rect: Rect, width: Int): Rect {
    return Rect(
        width - rect.right,
        rect.top,
        width - rect.left,
        rect.bottom
    )
}

fun l2Normalize(v: FloatArray): FloatArray {
    var sum = 0f
    for (x in v) sum += x * x
    val norm = sqrt(sum)
    for (i in v.indices) v[i] /= norm
    return v
}



