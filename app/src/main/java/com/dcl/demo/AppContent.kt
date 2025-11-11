package com.dcl.demo

import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dcl.demo.ui.CameraPreview
import com.dcl.facesdk.FaceSdk

@ExperimentalGetImage
@Composable
fun AppContent(sdk: FaceSdk) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            CameraPreview(sdk = sdk)
        }
    }
}
