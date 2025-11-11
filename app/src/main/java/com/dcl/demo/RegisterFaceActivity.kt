package com.dcl.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.ExperimentalGetImage
import com.dcl.facesdk.FaceSdk
import com.dcl.facesdk.detector.FaceDetectorEngine
import com.dcl.demo.ui.RegisterFaceScreen
import kotlin.io.encoding.ExperimentalEncodingApi

class RegisterFaceActivity : ComponentActivity() {

    @ExperimentalEncodingApi
    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sdk = FaceSdk(this)
        val detector = FaceDetectorEngine(this)

        setContent {
            RegisterFaceScreen(sdk = sdk, detector = detector)
        }
    }
}
