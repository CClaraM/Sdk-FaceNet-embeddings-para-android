package com.dcl.facesdk.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.dcl.facesdk.data.DetectedFace
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector.FaceDetectorOptions
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FaceDetectorEngine(
    context: Context,
    private val minFaceConfidence: Float = 0.3f, // más permisivo que MLKit
) {

    private val faceDetector: FaceDetector

    init {
        try {
            val baseOptions = BaseOptions.builder()
                // Usa el modelo original del repo
                .setModelAssetPath("blaze_face_short_range.tflite")
                .build()

            val options = FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            faceDetector = FaceDetector.createFromOptions(context, options)
            Log.d("FaceDetectorEngine", "✅ BlazeFace cargado correctamente")

        } catch (e: Exception) {
            throw RuntimeException("❌ Error inicializando BlazeFace: ${e.message}", e)
        }
    }

    /**
     * Detección principal de rostros
     */
    suspend fun detect(bitmap: Bitmap): List<DetectedFace> = withContext(Dispatchers.Default) {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: FaceDetectorResult = faceDetector.detect(mpImage)

            if (result.detections().isEmpty()) {
                Log.v("FaceDetectorEngine", "⚠️ Ningún rostro detectado")
                return@withContext emptyList()
            }

            val faces = result.detections().mapNotNull { detection ->
                val rect = detection.boundingBox()
                val conf = detection.categories().firstOrNull()?.score() ?: 1.0f

                if (conf >= minFaceConfidence) {
                    DetectedFace(
                        box = RectF(
                            rect.left,
                            rect.top,
                            rect.right,
                            rect.bottom
                        ),
                        confidence = conf
                    )
                } else null
            }

            Log.d("FaceDetectorEngine", "🧠 Detecciones encontradas: ${faces.size}")
            faces
        } catch (e: Exception) {
            Log.e("FaceDetectorEngine", "❌ Error detectando rostro: ${e.message}")
            emptyList()
        }
    }

    fun close() {
        try {
            faceDetector.close() // si usas MediaPipe o BlazeFace Interpreter
            Log.d("FaceDetectorEngine", "✅ Detector cerrado correctamente")
        } catch (e: Exception) {
            Log.w("FaceDetectorEngine", "⚠️ Error cerrando FaceDetector: ${e.message}")
        }
    }
}
