package com.dcl.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.ExperimentalGetImage
import com.dcl.demo.ui.CameraPreview
import com.dcl.facesdk.FaceSdk

class CameraPreviewActivity : ComponentActivity() {

    private lateinit var sdk: FaceSdk

    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar SDK aquí
        sdk = FaceSdk(this)

        setContent {
            CameraPreview(sdk = sdk)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Liberar modelos
        sdk.close()
    }
}