package com.example.ui.sighting

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Sighting
import com.example.ui.components.SyncStatusBadge
import com.example.ui.components.VehicleImage
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalSurfaceVariant
import com.example.ui.theme.DangerContainer
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NearBlack
import com.example.ui.theme.SpotrGreen
import com.example.ui.theme.SpotrGreenContainer
import com.example.ui.theme.SpotrOnGreenContainer
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WarningAmber
import java.util.Locale

@Composable
fun SightingDetailScreen(
    sighting: Sighting,
    currentUserId: String,
    isOwner: Boolean,
    onNavigateBack: () -> Unit,
    onResumeDraft: (Sighting) -> Unit = {},
    onDeleteConfirmed: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    val images = sighting.allDisplayImages
    val pagerState = rememberPagerState(pageCount = { images.size.coerceAtLeast(1) })

    val canModify = sighting.createdBy == currentUserId || isOwner

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Sighting?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to permanently delete this ${sighting.brand} ${sighting.model} sighting from the shared garage?",
                    color = TextMuted
                )
            },
            containerColor = CharcoalCard,
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = TextWhite)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    Scaffold(
        containerColor = NearBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NearBlack)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text(
                    text = "${sighting.brand} ${sighting.model}".take(24).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    ),
                    color = TextWhite
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onResumeDraft(sighting) }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Sighting", tint = SpotrGreen)
                    }
                    if (canModify) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // INCOMPLETE DRAFT BANNER & RESUME ACTION
            if (sighting.isDraft) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NearBlack)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CharcoalCard)
                            .border(1.5.dp, WarningAmber, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(WarningAmber)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "INCOMPLETE DRAFT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        ),
                                        color = WarningAmber
                                    )
                                }
                                Text(
                                    text = "Ready to finish",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            if (sighting.missingFields.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    sighting.missingFields.forEach { field ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(CarbonDark)
                                                .border(0.5.dp, WarningAmber.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = field,
                                                fontSize = 10.sp,
                                                color = WarningAmber,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { onResumeDraft(sighting) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SpotrGreen,
                                    contentColor = NearBlack
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume & Complete Draft", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            // PHOTO CAROUSEL
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(CarbonDark)
            ) {
                if (images.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        VehicleImage(
                            model = images[page],
                            contentDescription = "Vehicle Photo ${page + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            placeholderIconSize = 64
                        )
                    }

                    // Pager Indicators
                    if (images.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CarbonDark.copy(alpha = 0.7f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            repeat(images.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(if (pagerState.currentPage == index) SpotrGreen else TextDim)
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = TextDim, modifier = Modifier.size(64.dp))
                    }
                }

                // Category pill overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NearBlack.copy(alpha = 0.85f))
                        .border(1.dp, SpotrGreen, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = sighting.category.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpotrGreen,
                        letterSpacing = 1.sp
                    )
                }

                // Cloud Sync Status Badge
                SyncStatusBadge(
                    syncStatus = sighting.syncStatus,
                    compact = false,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                )
            }

            // DETAILS CONTAINER
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Brand & Model
                Column {
                    Text(
                        text = sighting.brand.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = SpotrGreen
                    )
                    Text(
                        text = sighting.model,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = TextWhite
                    )
                }

                // NUMBER PLATE DISPLAY
                if (sighting.numberPlateRaw.isNotBlank() || sighting.numberPlateNormalized.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CharcoalCard)
                            .border(1.5.dp, CharcoalBorder, RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "INDIAN REGISTRATION PLATE",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                    color = TextMuted
                                )
                                Text(
                                    text = sighting.numberPlateRaw.ifBlank { sighting.numberPlateNormalized },
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 2.sp
                                    ),
                                    color = TextWhite
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SpotrGreenContainer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = sighting.plateOcrStatus.replace("_", " ").uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SpotrOnGreenContainer
                                )
                            }
                        }
                    }
                }

                // SIGHTING METADATA CARD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalCard)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MetaRow(
                        icon = Icons.Default.Person,
                        label = "Spotted By",
                        value = sighting.creatorName.ifBlank { "Co-Spotter" }
                    )
                    MetaRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Date & Time",
                        value = "${sighting.sightingDate} at ${sighting.sightingTime}"
                    )
                    MetaRow(
                        icon = Icons.Default.LocationOn,
                        label = "Location",
                        value = listOfNotNull(sighting.locationName, sighting.area, sighting.city)
                            .filter { it.isNotBlank() }
                            .joinToString(", ")
                            .ifBlank { "Coordinates Logged" }
                    )

                    if (sighting.latitude != 0.0 || sighting.longitude != 0.0) {
                        MetaRow(
                            icon = Icons.Default.Map,
                            label = "GPS Coordinates",
                            value = "${String.format(Locale.US, "%.5f", sighting.latitude)}, ${String.format(Locale.US, "%.5f", sighting.longitude)}"
                        )
                    }

                    if (sighting.notes.isNotBlank()) {
                        MetaRow(
                            icon = Icons.Default.Notes,
                            label = "Notes",
                            value = sighting.notes
                        )
                    }
                }

                // GOOGLE MAPS ACTION
                if (sighting.latitude != 0.0 || sighting.longitude != 0.0) {
                    Button(
                        onClick = {
                            val uri = Uri.parse("geo:${sighting.latitude},${sighting.longitude}?q=${sighting.latitude},${sighting.longitude}(${Uri.encode("${sighting.brand} ${sighting.model}")})")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            try {
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                // Fallback to browser Google Maps URL
                                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${sighting.latitude},${sighting.longitude}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CharcoalSurfaceVariant,
                            contentColor = SpotrGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .border(1.dp, SpotrGreen, RoundedCornerShape(12.dp))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = SpotrGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open In Google Maps", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpotrGreen,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = TextWhite)
        }
    }
}
