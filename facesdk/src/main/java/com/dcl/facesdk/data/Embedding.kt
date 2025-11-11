package com.dcl.facesdk.data

/**
 * Representa un vector de características faciales (embedding) generado por FaceNet.
 */
data class Embedding(
    val values: FloatArray
)