package com.dcl.facesdk.embeddings

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.pow
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import com.dcl.facesdk.utils.l2Normalize
import android.util.Log



class FaceNetEngine(
    context: Context,
    modelName: String = "facenet_512.tflite",
    val embeddingDim: Int = 512
) {

    private val interpreter: Interpreter

    private val processor = ImageProcessor.Builder()
        .add(ResizeOp(160, 160, ResizeOp.ResizeMethod.BILINEAR))
        .add(StandardizeOp())              // ✅ igual que el original
        .build()

    private class StandardizeOp : TensorOperator {
        override fun apply(input: org.tensorflow.lite.support.tensorbuffer.TensorBuffer?): org.tensorflow.lite.support.tensorbuffer.TensorBuffer {
            val pixels = input!!.floatArray
            val mean = pixels.average().toFloat()
            var std = sqrt(pixels.map { (it - mean).pow(2) }.sum() / pixels.size.toFloat())
            std = max(std, 1f / sqrt(pixels.size.toFloat()))
            for (i in pixels.indices) {
                pixels[i] = (pixels[i] - mean) / std
            }
            val out = TensorBufferFloat.createFixedSize(input.shape, DataType.FLOAT32)
            out.loadArray(pixels)
            return out
        }
    }

    init {
        interpreter = Interpreter(loadModelFile(context, modelName))
    }

    private fun loadModelFile(context: Context, filename: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel

        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }


    suspend fun getEmbedding(faceBitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {

        var inputImage = TensorImage(DataType.FLOAT32)
        inputImage.load(faceBitmap)

        inputImage = processor.process(inputImage)

        val embeddingOutput = Array(1) { FloatArray(embeddingDim) }
        interpreter.run(inputImage.buffer, embeddingOutput)

        l2Normalize(embeddingOutput[0]) // normalización final
    }

    fun close() {
        try {
            interpreter.close()
            Log.d("FaceNetEngine", "✅ FaceNet cerrado correctamente")
        } catch (e: Exception) {
            Log.w("FaceNetEngine", "⚠️ Error cerrando FaceNetEngine: ${e.message}")
        }
    }

}
