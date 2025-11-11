package com.dcl.facesdk.data

/**
 * Resultado del modelo anti-spoof (liveness).
 *
 * @param isLive true si el rostro es real (no spoof)
 * @param liveScore nivel de confianza de rostro real (0.0–1.0)
 * @param printScore nivel de probabilidad de ataque impreso (0.0–1.0)
 * @param replayScore nivel de probabilidad de ataque por video (0.0–1.0)
 */
data class SpoofResult(
    val isLive: Boolean,
    val liveScore: Float,
    val printScore: Float,
    val replayScore: Float
)
