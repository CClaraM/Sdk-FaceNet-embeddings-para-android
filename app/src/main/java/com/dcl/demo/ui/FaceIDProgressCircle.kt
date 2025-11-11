package com.dcl.demo.ui
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
@Composable
fun FaceIDProgressCircle(
    progress: Float, // 0f..1f
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.CircularProgressIndicator(
        progress = progress,
        strokeWidth = 6.dp,
        modifier = modifier.size(90.dp)
    )
}