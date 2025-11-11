package com.dcl.facesdk.data

import android.graphics.Rect

//data class DetectedFace(
//    val box: Rect,
//    val confidence: Float
//)

//data class SpoofResult(
//    val isLive: Boolean,
//    val scores: FloatArray // [live_score, print_attack, replay_attack] segun modelo
//)

//data class Embedding(
//    val vector: FloatArray // 512D
//)

data class RecognitionMatch(
    val personId: String,
    val similarity: Float, // coseno
)
