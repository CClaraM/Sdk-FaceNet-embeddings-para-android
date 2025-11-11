package com.dcl.demo.utils

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.util.concurrent.Executors
import com.dcl.facesdk.utils.decodeFileRespectExif   // ✅ importa util de SDK

/**
 * Captura una foto HD real desde ImageCapture y la entrega como Bitmap corregido por EXIF.
 * - Corrige rotación (especialmente cámaras frontales Samsung, Xiaomi, etc.)
 * - Mantiene resolución y color original
 */
fun captureHDPhoto(imageCapture: ImageCapture, onCaptured: (Bitmap) -> Unit) {
    val file = File.createTempFile("face_capture_", ".jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCapture.takePicture(
        options,
        Executors.newSingleThreadExecutor(),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val corrected = decodeFileRespectExif(file.absolutePath) // ✅ rotación corregida
                    onCaptured(corrected)
                } catch (e: Exception) {
                    Log.e("HD-CAPTURE", "Error decodificando imagen: ${e.message}")
                }
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("HD-CAPTURE", "Error: ${exc.message}")
            }
        }
    )
}