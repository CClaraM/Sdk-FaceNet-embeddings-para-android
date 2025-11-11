package com.dcl.facesdk.liveness

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class SpoofResult(
    val isLive: Boolean,
    val liveScore: Float,      // prob. de la clase "live" (según liveIndex)
    val spoofScore: Float,     // suma de las clases ≠ liveIndex
    val label: Int,            // argmax promedio
    val classCount: Int,       // nº de clases del modelo (2 o 3)
    val probsAvg: FloatArray   // vector promedio final (para depurar/telemetría)
)

/**
 * Detecta liveness con dos modelos (escala 2.7 y 4.0):
 *  - Entrada: frame + bbox
 *  - Crop en escalas 2.7 y 4.0 -> 80x80
 *  - BGR float32 (0..255), sin dividir por 255
 *  - Salida: softmax por modelo, promedio, argmax.
 *
 * Soporta salidas de 2 o 3 clases. Por defecto, liveIndex=1.
 * Si tu modelo mapea live a otra posición (p.ej. 2), ajústalo al crear el detector.
 */
class SpoofDetector(
    private val context: Context,
    private val modelAssetPathScale27: String = "spoof_model_scale_2_7.tflite",
    private val modelAssetPathScale40: String = "spoof_model_scale_4_0.tflite",
    private val inputSize: Int = 80,
    private val liveIndex: Int = 1,           // <— ¡ajústalo si tu modelo usa otro índice para LIVE!
    private val classNames: List<String>? = null // opcional: nombres para loggear (“spoof, live” o “bg, spoof, live”, etc.)
) {

    private val tag = "SpoofDetector"
    private val tflite27: Interpreter = Interpreter(loadModelFile(modelAssetPathScale27))
    private val tflite40: Interpreter = Interpreter(loadModelFile(modelAssetPathScale40))

    @Throws(IOException::class)
    private fun loadModelFile(assetPath: String): MappedByteBuffer {
        val fd = context.assets.openFd(assetPath)
        FileInputStream(fd.fileDescriptor).use { fis ->
            return fis.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    fun close() {
        try { tflite27.close() } catch (_: Exception) {}
        try { tflite40.close() } catch (_: Exception) {}
    }

    /** API pública: NO modifica el frame; hace sus propias copias/crops internas. */

    //Funciona correctamente sin ponderacion
    fun detect(frameBitmap: Bitmap, faceRect: Rect): SpoofResult {
        // ---- Logs de entrada ----
        Log.d(tag, "detect() frame=${frameBitmap.width}x${frameBitmap.height} rect=$faceRect")

        // 1) Crops en dos escalas (copias independientes)
        val crop27 = cropByScale(frameBitmap, faceRect, 2.7f, inputSize)
        val crop40 = cropByScale(frameBitmap, faceRect, 4.0f, inputSize)
        Log.d(tag, "crops: 2.7=${crop27.width}x${crop27.height}, 4.0=${crop40.width}x${crop40.height}")

        // 2) Buffers BGR float32 (capacidad EXACTA: 80*80*3*4 = 76800 bytes)
        val input27 = toFloat32BGRBuffer(crop27, inputSize)
        val input40 = toFloat32BGRBuffer(crop40, inputSize)
        Log.d(tag, "buffers: input27.capacity=${input27.capacity()}, input40.capacity=${input40.capacity()}")

        // 3) Preparar salidas según shape real del modelo (dinámico: 2 ó 3)
        val outShape27 = tflite27.getOutputTensor(0).shape()  // [1, N]
        val outShape40 = tflite40.getOutputTensor(0).shape()  // [1, N]
        val classes27 = if (outShape27.size >= 2) outShape27[1] else 2
        val classes40 = if (outShape40.size >= 2) outShape40[1] else 2

        if (classes27 != classes40) {
            Log.w(tag, "⚠️ classCount distinto entre modelos: scale2.7=$classes27 vs scale4.0=$classes40. Se usará el mínimo.")
        }
        val classCount = min(classes27, classes40)
        require(classCount >= 2) { "El modelo debe tener al menos 2 clases, detectado classCount=$classCount" }

        val out27 = Array(1) { FloatArray(classCount) }
        val out40 = Array(1) { FloatArray(classCount) }

        // 4) Inferencias
        val inShape27 = tflite27.getInputTensor(0).shape() // esperado [1,80,80,3]
        val inShape40 = tflite40.getInputTensor(0).shape()
        Log.d(tag, "in27=${inShape27.joinToString()}; out27=${outShape27.joinToString()}")
        Log.d(tag, "in40=${inShape40.joinToString()}; out40=${outShape40.joinToString()}")

        synchronized(tflite27) { tflite27.run(input27, out27) }
        synchronized(tflite40) { tflite40.run(input40, out40) }

        // 5) Softmax por modelo
        val s27 = softmax(out27[0])
        val s40 = softmax(out40[0])

        // 6) Promedio por clase
        val avg = FloatArray(classCount) { i -> (s27[i] + s40[i]) / 2f }

        // Logs por clase
        val names = classNames ?: (0 until classCount).map { i ->
            when (classCount) {
                2 -> if (i == liveIndex) "LIVE" else "SPOOF"
                3 -> when (i) {
                    liveIndex -> "LIVE"
                    else -> "NON-LIVE[$i]"
                }
                else -> "C$i"
            }
        }
        Log.d(tag, "softmax 2.7 = ${fmt(s27, names)}")
        Log.d(tag, "softmax 4.0 = ${fmt(s40, names)}")
        Log.d(tag, "avg probs   = ${fmt(avg, names)} (liveIndex=$liveIndex)")

        // 7) Decisión
        val label = argmax(avg)
        val liveScore = avg.getOrElse(liveIndex) { 0f }
        val spoofScore = if (classCount == 2) {
            1f - liveScore
        } else {
            // Para 3 clases, tomamos como "spoof" la suma de todas las clases ≠ liveIndex
            var s = 0f
            for (i in 0 until classCount) if (i != liveIndex) s += avg[i]
            s
        }
        val isLive = (label == liveIndex)

        Log.d(tag, "RESULT => label=$label (${names.getOrNull(label) ?: "C$label"}) isLive=$isLive liveScore=${liveScore.f3()} spoofScore=${spoofScore.f3()}")

        return SpoofResult(
            isLive = isLive,
            liveScore = liveScore,
            spoofScore = spoofScore,
            label = label,
            classCount = classCount,
            probsAvg = avg
        )
    }

    /** Crop expandido por escala, clamp a bordes, resize a target x target. */
    private fun cropByScale(src: Bitmap, rect: Rect, scale: Float, target: Int): Bitmap {
        val cx = (rect.left + rect.right) / 2f
        val cy = (rect.top + rect.bottom) / 2f
        val halfW = rect.width() * 0.5f * scale
        val halfH = rect.height() * 0.5f * scale

        val left   = max(0f, cx - halfW).toInt()
        val top    = max(0f, cy - halfH).toInt()
        val right  = min(src.width.toFloat(),  cx + halfW).toInt()
        val bottom = min(src.height.toFloat(), cy + halfH).toInt()

        val w = max(1, right - left)
        val h = max(1, bottom - top)

        val crop = Bitmap.createBitmap(src, left, top, w, h)
        return Bitmap.createScaledBitmap(crop, target, target, true)
    }

    /**
     * Convierte a Float32 NHWC **BGR** (0..255). Capacidad exacta = size*size*3*4.
     * No modifica el bitmap fuente (crea copias si hace falta).
     */
    private fun toFloat32BGRBuffer(bmpSrc: Bitmap, size: Int): ByteBuffer {
        val bmp = if (bmpSrc.config != Bitmap.Config.ARGB_8888) {
            bmpSrc.copy(Bitmap.Config.ARGB_8888, false)
        } else bmpSrc

        val out = ByteBuffer.allocateDirect(size * size * 3 * 4)
        out.order(ByteOrder.nativeOrder())
        out.rewind()

        val b = if (bmp.width != size || bmp.height != size) {
            Bitmap.createScaledBitmap(bmp, size, size, true)
        } else bmp

        val pixels = IntArray(size * size)
        b.getPixels(pixels, 0, size, 0, 0, size, size)

        var idx = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val p = pixels[idx++]
                val r = (p shr 16 and 0xFF).toFloat()
                val g = (p shr 8 and 0xFF).toFloat()
                val bl= (p and 0xFF).toFloat()
                // Orden B, G, R
                out.putFloat(bl)
                out.putFloat(g)
                out.putFloat(r)
            }
        }
        out.rewind()
        return out
    }

    private fun softmax(logits: FloatArray): FloatArray {
        var maxV = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > maxV) maxV = v
        var sum = 0f
        val exps = FloatArray(logits.size)
        for (i in logits.indices) {
            val e = exp((logits[i] - maxV).toDouble()).toFloat()
            exps[i] = e
            sum += e
        }
        if (sum == 0f) return FloatArray(logits.size) { 0f }
        for (i in exps.indices) exps[i] = exps[i] / sum
        return exps
    }

    private fun argmax(arr: FloatArray): Int {
        var best = 0
        var bestVal = Float.NEGATIVE_INFINITY
        for (i in arr.indices) {
            if (arr[i] > bestVal) {
                bestVal = arr[i]
                best = i
            }
        }
        return best
    }

    private fun Float.f3(): String = String.format("%.3f", this)
    private fun fmt(probs: FloatArray, names: List<String>): String {
        return probs.indices.joinToString(prefix = "[", postfix = "]") { i ->
            val n = names.getOrNull(i) ?: "C$i"
            "${n}:${probs[i].f3()}"
        }
    }
}
