package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Utility class to manage CameraX initialization, image capture, and photo handling
 * before executing OCR or adding photos to vehicle sightings.
 */
object CameraCaptureHelper {

    private const val TAG = "CameraCaptureHelper"
    private const val FILENAME_FORMAT = "yyyyMMdd_HHmmss"

    /**
     * Creates a unique temporary image file in the app's cache or internal files directory.
     */
    fun createOutputPhotoFile(context: Context): File {
        val mediaDir = File(context.filesDir, "sightings").apply { if (!exists()) mkdirs() }
        val timeStamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(Date())
        val fileName = "CAR_${timeStamp}_${UUID.randomUUID().toString().take(6)}.jpg"
        return File(mediaDir, fileName)
    }

    /**
     * Binds CameraX Preview and ImageCapture use cases to a LifecycleOwner and PreviewView.
     * Returns the bound Camera instance for flash or zoom control.
     */
    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        onCameraConfigured: (ImageCapture, Camera) -> Unit = { _, _ -> }
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Configure Preview
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // Configure ImageCapture for high quality vehicle and plate photos
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                // Unbind any previous use cases
                cameraProvider.unbindAll()

                // Bind to lifecycle
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                onCameraConfigured(imageCapture, camera)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Asynchronously captures a photo and saves it to a file.
     */
    suspend fun capturePhotoToFile(
        context: Context,
        imageCapture: ImageCapture,
        outputFile: File = createOutputPhotoFile(context)
    ): Result<File> = suspendCancellableCoroutine { continuation ->
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    continuation.resume(Result.success(outputFile))
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    continuation.resume(Result.failure(exc))
                }
            }
        )
    }

    /**
     * Captures a high-resolution in-memory Bitmap directly from CameraX ImageProxy,
     * correcting for sensor rotation degrees so it is ready for instant OCR and preview.
     */
    suspend fun captureBitmap(
        context: Context,
        imageCapture: ImageCapture
    ): Result<Bitmap> = suspendCancellableCoroutine { continuation ->
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val bitmap = imageProxy.toBitmap()
                        val correctedBitmap = if (rotationDegrees != 0) {
                            rotateBitmap(bitmap, rotationDegrees.toFloat())
                        } else {
                            bitmap
                        }
                        continuation.resume(Result.success(correctedBitmap))
                    } catch (e: Exception) {
                        continuation.resume(Result.failure(e))
                    } finally {
                        imageProxy.close()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Bitmap capture failed: ${exc.message}", exc)
                    continuation.resume(Result.failure(exc))
                }
            }
        )
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
