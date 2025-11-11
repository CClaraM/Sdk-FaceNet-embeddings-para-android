package com.dcl.facesdk.data

import android.graphics.RectF

data class DetectedFace(
    val box: RectF,
    val confidence: Float
)
