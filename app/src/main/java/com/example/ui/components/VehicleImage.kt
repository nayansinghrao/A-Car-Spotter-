package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.SpotrGreen
import com.example.ui.theme.TextDim
import com.example.util.ImageStorageHelper

/**
 * Standard vehicle image display component for SPOTR.
 * Transparently resolves Base64 strings, file paths, URIs, and web URLs with
 * high-performance LRU caching, loading indicators, and graceful placeholders.
 */
@Composable
fun VehicleImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIconSize: Int = 40
) {
    val resolvedModel = remember(model) {
        ImageStorageHelper.resolveImageModel(model)
    }

    if (resolvedModel == null) {
        Box(
            modifier = modifier.background(CarbonDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = contentDescription,
                tint = TextDim,
                modifier = Modifier.size(placeholderIconSize.dp)
            )
        }
    } else {
        SubcomposeAsyncImage(
            model = resolvedModel,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize().background(CarbonDark),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = SpotrGreen,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize().background(CarbonDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = contentDescription,
                        tint = TextDim,
                        modifier = Modifier.size(placeholderIconSize.dp)
                    )
                }
            }
        )
    }
}
