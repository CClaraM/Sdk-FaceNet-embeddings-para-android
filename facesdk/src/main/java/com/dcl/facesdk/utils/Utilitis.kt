package com.dcl.facesdk.utils

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalEncodingApi
fun FloatArray.toBase64(): String {
    val buffer = ByteBuffer.allocate(this.size * 4).order(ByteOrder.nativeOrder())
    this.forEach { buffer.putFloat(it) }
    return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
}

fun String.toFloatArray(): FloatArray {
    val bytes = Base64.decode(this, Base64.NO_WRAP)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
    val floatArray = FloatArray(bytes.size / 4)
    for (i in floatArray.indices) floatArray[i] = buffer.getFloat(i * 4)
    return floatArray
}

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    for (i in a.indices) dot += a[i] * b[i]
    return dot // porque los embeddings ya están L2-normalizados
}

fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
