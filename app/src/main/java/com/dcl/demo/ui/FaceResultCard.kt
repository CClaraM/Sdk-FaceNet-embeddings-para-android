package com.dcl.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dcl.facesdk.data.MatchResult

@Composable
fun FaceResultCard(result: MatchResult) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            Text("Detección", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("Confianza rostro: ${"%.2f".format(result.faceConfidence)}", color = Color.White)
            Text("Liveness: ${"%.2f".format(result.liveScore)}", color = if (result.isLive) Color.Green else Color.Red)
            Text("Similitud: ${"%.2f".format(result.similarity)}", color = Color.White)
            Text("Coincide umbral: ${result.thresholdMatched}", color = Color.White)
        }
    }
}
