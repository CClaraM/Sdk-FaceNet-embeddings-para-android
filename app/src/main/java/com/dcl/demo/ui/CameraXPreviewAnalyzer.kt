package com.dcl.demo.ui

import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@Composable
fun CameraXPreviewAnalyzer(
    modifier: Modifier = Modifier,
    onPreviewReady: (PreviewView) -> Unit,
    onImageCaptureReady: (ImageCapture) -> Unit = {},    // ✅ nuevo
    onFrame: (ImageProxy) -> Unit,
) {

    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current

    AndroidView(modifier = modifier, factory = { context ->

        val previewView = PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // ✅ PREVIEW
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            // ✅ ANALYSIS (detección en tiempo real)
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().apply {
                    setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                        onFrame(proxy)
                    }
                }

            // ✅ IMAGE CAPTURE (foto HD real)
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // ✅ Bind everything to the camera
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycle,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis,
                imageCapture
            )

            // Return imageCapture to composable
            onImageCaptureReady(imageCapture)

        }, ContextCompat.getMainExecutor(context))

        onPreviewReady(previewView)
        previewView
    })
}
