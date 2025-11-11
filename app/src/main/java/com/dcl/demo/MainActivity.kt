package com.dcl.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.core.content.ContextCompat
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dcl.demo.ui.theme.AppTheme



@ExperimentalGetImage
class MainActivity : ComponentActivity() {

    //private lateinit var faceSdk: FaceSdk

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchApp() else finish()
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            launchApp()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }


    private fun launchApp() {
        setContent {
            AppTheme {
                MainMenu(
                    onOpenPreview = { startActivity(Intent(this, CameraPreviewActivity::class.java)) },
                    onAlignFace = { startActivity(Intent(this, AlignFaceActivity::class.java)) },
                    onRegisterFace = { startActivity(Intent(this, RegisterFaceActivity::class.java)) },
                    onVerifyFace = { startActivity(Intent(this, VerifyFaceActivity::class.java)) },
                    onVerifyFaceLive = { startActivity(Intent(this, IdentifyLiveActivity::class.java)) }
                )
            }
        }
    }
}

@Composable
fun MainMenu(
    onOpenPreview: () -> Unit,
    onRegisterFace: () -> Unit,
    onVerifyFace: () -> Unit,
    onAlignFace: () -> Unit,
    onVerifyFaceLive: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {

        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onOpenPreview) {
                Text("Deteccion (Detección + Spoof)")
            }
            Button(onClick = onAlignFace) {
                Text("Alinear rostro (Guía)")
            }
            Button(onClick = onRegisterFace) {
                Text("Registrar rostro (Crear Embedding)")
            }
            Button(onClick = onVerifyFace) {
                Text("Verificar rostro (Comparación)")
            }
            Button(onClick = onVerifyFaceLive) {
                Text("Live identificador  (Comparación live)")
            }

        }
    }
}