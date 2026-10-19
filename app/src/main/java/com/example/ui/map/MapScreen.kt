package com.example.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Sighting
import com.example.data.model.VehicleCategory
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalSurfaceVariant
import com.example.ui.theme.NearBlack
import com.example.ui.theme.SpotrGreen
import com.example.ui.theme.SpotrGreenContainer
import com.example.ui.theme.SpotrOnGreenContainer
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import java.util.Locale

@Composable
fun MapScreen(
    sightings: List<Sighting>,
    onSightingClick: (Sighting) -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val geoSightings = remember(sightings, selectedCategory) {
        sightings.filter { sighting ->
            (sighting.latitude != 0.0 || sighting.longitude != 0.0) &&
                    (selectedCategory == null || sighting.category == selectedCategory)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlack)
            .padding(top = 16.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LOCATION HISTORY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = SpotrGreen
                )
                Text(
                    text = "Spotting Coordinates",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = TextWhite
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${geoSightings.size} mapped",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotrGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // CATEGORY FILTERS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isAll = selectedCategory == null
            FilterChip(
                selected = isAll,
                onClick = { selectedCategory = null },
                label = { Text("All Spots", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpotrGreen,
                    selectedLabelColor = NearBlack,
                    containerColor = CharcoalCard,
                    labelColor = TextWhite
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isAll,
                    borderColor = CharcoalBorder,
                    selectedBorderColor = SpotrGreen
                )
            )

            VehicleCategory.entries.forEach { cat ->
                val isSelected = selectedCategory == cat.label
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = if (isSelected) null else cat.label },
                    label = { Text(cat.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpotrGreen,
                        selectedLabelColor = NearBlack,
                        containerColor = CharcoalCard,
                        labelColor = TextWhite
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = CharcoalBorder,
                        selectedBorderColor = SpotrGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // MAP RADAR OVERVIEW CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CarbonDark)
                .border(1.dp, SpotrGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(SpotrGreenContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = SpotrOnGreenContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GPS Telemetry Synced",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "Every pin is backed by authentic device GPS coordinates. Tap any sighting to open directly in Google Maps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // LIST OF GEOLOCATED SIGHTINGS
        if (geoSightings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = TextDim, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No GPS sightings logged yet", color = TextWhite, fontWeight = FontWeight.Bold)
                    Text(
                        "When adding a vehicle sighting, tap 'Capture GPS' to log exact telemetry coordinates.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(geoSightings, key = { it.id }) { sighting ->
                    MapSightingCard(
                        sighting = sighting,
                        onClick = { onSightingClick(sighting) },
                        onOpenMaps = {
                            val uri = Uri.parse("geo:${sighting.latitude},${sighting.longitude}?q=${sighting.latitude},${sighting.longitude}(${Uri.encode("${sighting.brand} ${sighting.model}")})")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${sighting.latitude},${sighting.longitude}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MapSightingCard(
    sighting: Sighting,
    onClick: () -> Unit,
    onOpenMaps: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalCard)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CarbonDark)
        ) {
            if (sighting.primaryImagePath.isNotBlank()) {
                AsyncImage(
                    model = sighting.primaryImagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = TextDim)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${sighting.brand} ${sighting.model}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (sighting.numberPlateRaw.isNotBlank() || sighting.numberPlateNormalized.isNotBlank()) {
                Text(
                    text = sighting.numberPlateRaw.ifBlank { sighting.numberPlateNormalized },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SpotrGreen
                )
            }

            Text(
                text = listOfNotNull(sighting.locationName, sighting.area, sighting.city)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank { "${String.format(Locale.US, "%.4f", sighting.latitude)}, ${String.format(Locale.US, "%.4f", sighting.longitude)}" },
                fontSize = 11.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Open in Google Maps button
        Button(
            onClick = onOpenMaps,
            colors = ButtonDefaults.buttonColors(
                containerColor = CharcoalSurfaceVariant,
                contentColor = SpotrGreen
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            modifier = Modifier.border(1.dp, SpotrGreen, RoundedCornerShape(8.dp))
        ) {
            Icon(imageVector = Icons.Default.Navigation, contentDescription = "Open Maps", modifier = Modifier.size(16.dp))
        }
    }
}
