package com.dcl.demo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.ExperimentalGetImage
import com.dcl.facesdk.FaceSdk
import com.dcl.demo.ui.IdentifyLiveScreen


class IdentifyLiveActivity : ComponentActivity() {

    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sdk = FaceSdk(this)

        setContent {
            IdentifyLiveScreen(sdk = sdk)
        }
    }
}