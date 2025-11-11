package com.dcl.facesdk.data

data class CosineResult(
    val matched: Boolean,
    val bestIndex: Int?,
    val score: Float
)
