package com.dcl.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.ExperimentalGetImage
import com.dcl.facesdk.FaceSdk
import com.dcl.facesdk.detector.FaceDetectorEngine
import com.dcl.demo.ui.VerifyFaceScreen

class VerifyFaceActivity : ComponentActivity() {
    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sdk = FaceSdk(this)
        val detector = FaceDetectorEngine(this)
        setContent {
            VerifyFaceScreen(sdk = sdk, detector = detector)
        }
    }
}