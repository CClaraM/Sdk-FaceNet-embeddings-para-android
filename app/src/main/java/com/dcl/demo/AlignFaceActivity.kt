package com.dcl.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.ExperimentalGetImage
import com.dcl.facesdk.detector.FaceDetectorEngine
import com.dcl.demo.ui.AlignFaceScreen
import com.dcl.facesdk.FaceSdk

class AlignFaceActivity : ComponentActivity() {

    private lateinit var sdk: FaceSdk

    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sdk = FaceSdk(this)
        val detector = FaceDetectorEngine(this)

        setContent {
            AlignFaceScreen(sdk = sdk, detector = detector)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}