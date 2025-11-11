package com.dcl.demo.ui.theme

import androidx.compose.runtime.Composable
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        content = content
    )
}