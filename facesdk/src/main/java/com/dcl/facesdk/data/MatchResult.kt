
package com.dcl.facesdk.data

import android.graphics.RectF
/**
 * Resultado unificado: detección + liveness + similitud
 */
data class MatchResult(
    val faceBox: RectF? = null,
    val faceConfidence: Float = 0f,
    val liveScore: Float = 0f,
    val similarity: Float = 0f,
    val isLive: Boolean = true,
    val thresholdMatched: Boolean = false
)
