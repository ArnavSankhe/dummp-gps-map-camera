package com.example.gpsmapcamera.camera

import android.content.Context
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraXController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    suspend fun bind(previewView: PreviewView) {
        try {
            cameraProvider = context.getCameraProvider()
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        imageCapture.targetRotation = rotation
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(rotation)
            .build()
            .also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
            
            // Try Back Camera first
            val backSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(lifecycleOwner, backSelector, preview, imageCapture)
            } catch (exc: Exception) {
                // Fallback to Front Camera if Back fails (common on some emulators)
                val frontSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(lifecycleOwner, frontSelector, preview, imageCapture)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun executor(): Executor = ContextCompat.getMainExecutor(context)
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }
