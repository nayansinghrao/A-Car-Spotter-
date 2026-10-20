package com.example.ui.camera

import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.NearBlack
import com.example.ui.theme.SpotrGreen
import com.example.ui.theme.TextWhite
import com.example.util.CameraCaptureHelper
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraViewfinderScreen(
    onPhotoCaptured: (File) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlack)
    ) {
        // Live CameraX Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { previewView ->
                CameraCaptureHelper.startCamera(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    lensFacing = lensFacing,
                    onCameraConfigured = { capture, camera ->
                        imageCapture = capture
                        boundCamera = camera
                    }
                )
            }
        )

        // Overlay: Target Frame / HUD for car and plate
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Viewfinder Reticle
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(220.dp)
                        .border(2.dp, SpotrGreen.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    // Plate Target Guide
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.75f)
                            .height(50.dp)
                            .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.85f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ ALIGN NUMBER PLATE HERE ]",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = Color(0xFF00E5FF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "FRAME CAR & LICENSE PLATE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = TextWhite,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NearBlack.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Top Controls: Close, Flash, Flip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(NearBlack.copy(alpha = 0.6f))
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextWhite)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Flash / Torch toggle
                IconButton(
                    onClick = {
                        val camera = boundCamera
                        if (camera != null && camera.cameraInfo.hasFlashUnit()) {
                            val newTorch = !isFlashOn
                            camera.cameraControl.enableTorch(newTorch)
                            isFlashOn = newTorch
                        } else {
                            Toast.makeText(context, "Flash unavailable on this lens", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(NearBlack.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = if (isFlashOn) SpotrGreen else TextWhite
                    )
                }

                // Camera Lens Switch (Front / Back)
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(NearBlack.copy(alpha = 0.6f))
                ) {
                    Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = TextWhite)
                }
            }
        }

        // Bottom Controls: Shutter Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(3.dp, SpotrGreen, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (isCapturing) CarbonDark else SpotrGreen)
                    .clickable(enabled = !isCapturing && imageCapture != null) {
                        val capture = imageCapture ?: return@clickable
                        isCapturing = true
                        coroutineScope.launch {
                            val result = CameraCaptureHelper.capturePhotoToFile(context, capture)
                            isCapturing = false
                            if (result.isSuccess) {
                                val file = result.getOrThrow()
                                onPhotoCaptured(file)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Capture failed: ${result.exceptionOrNull()?.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .testTag("camera_shutter_button"),
                contentAlignment = Alignment.Center
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = SpotrGreen,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}
