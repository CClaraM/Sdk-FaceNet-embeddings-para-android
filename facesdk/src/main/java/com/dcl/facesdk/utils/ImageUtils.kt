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

/*fun imageProxyToBitmapRGBA(image: ImageProxy): Bitmap {
    // OUTPUT_IMAGE_FORMAT_RGBA_8888 garantizado
    val plane = image.planes[0].buffer
    val width = image.width
    val height = image.height

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(plane)

    // Rotación (NO espejo)
    val rot = image.imageInfo.rotationDegrees
    if (rot != 0) {
        val m = Matrix().apply { postRotate(rot.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    return bmp
}*/

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

/*fun safeCropBitmap(src: Bitmap, rect: Rect): Bitmap? {
    val left = rect.left.coerceIn(0, src.width - 1)
    val top = rect.top.coerceIn(0, src.height - 1)
    val w = rect.width().coerceAtLeast(1).coerceAtMost(src.width - left)
    val h = rect.height().coerceAtLeast(1).coerceAtMost(src.height - top)
    return try { Bitmap.createBitmap(src, left, top, w, h) } catch (_: Exception) { null }
}*/

fun l2Normalize(v: FloatArray): FloatArray {
    var sum = 0f
    for (x in v) sum += x * x
    val norm = sqrt(sum)
    for (i in v.indices) v[i] /= norm
    return v
}

/*
@ExperimentalGetImage
fun ImageProxy.toBitmapProcessed(mirror: Boolean = true): Bitmap? {
    val planeY = planes[0].buffer
    val planeU = planes[1].buffer
    val planeV = planes[2].buffer

    val ySize = planeY.remaining()
    val uSize = planeU.remaining()
    val vSize = planeV.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    planeY.get(nv21, 0, ySize)
    planeV.get(nv21, ySize, vSize)
    planeU.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    var bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

    // rotar
    val matrix = Matrix()
    matrix.postRotate(imageInfo.rotationDegrees.toFloat())
    if (mirror) {
        matrix.postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f)
    }
    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)

    // debug
    Log.d("toBitmapProcessed", "Bitmap listo: ${bmp.width}x${bmp.height}, mirror=$mirror, rot=${imageInfo.rotationDegrees}")

    return bmp
}*/

/**
 * Redimensiona la imagen al tamaño requerido por FaceNet (160x160)
 */
/*fun Bitmap.toFaceNetInputBitmap(targetSize: Int = 160): Bitmap {
    return Bitmap.createScaledBitmap(this, targetSize, targetSize, true)
}*/

/**
 * Convierte el Bitmap a ByteBuffer con normalización [-1, 1]
 * Igual al preprocessing de shubham0204
 */
/*fun Bitmap.toFaceNetByteBuffer(): ByteBuffer {

    val inputSize = 160
    val channels = 3
    val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * channels * 4)
    inputBuffer.order(ByteOrder.nativeOrder())

    val pixels = IntArray(inputSize * inputSize)
    this.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

    for (p in pixels) {
        inputBuffer.putFloat((Color.red(p) - 127.5f) / 128f)
        inputBuffer.putFloat((Color.green(p) - 127.5f) / 128f)
        inputBuffer.putFloat((Color.blue(p) - 127.5f) / 128f)
    }

    return inputBuffer
}*/


