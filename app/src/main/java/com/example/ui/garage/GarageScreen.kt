package com.example.ui.garage

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Sighting
import com.example.data.model.VehicleCategory
import com.example.ocr.PlateOcrEngine
import com.example.ui.components.SyncStatusBadge
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
import com.example.ui.theme.WarningAmber
import java.util.Locale

enum class SightingSort(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    BRAND_AZ("Brand (A-Z)"),
    BRAND_ZA("Brand (Z-A)")
}

@Composable
fun GarageScreen(
    sightings: List<Sighting>,
    garageName: String,
    onSightingClick: (Sighting) -> Unit,
    onResumeDraft: (Sighting) -> Unit = {},
    onAddSightingClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf("all") } // "all", "draft", "complete"
    var selectedSort by remember { mutableStateOf(SightingSort.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    val draftCount = sightings.count { it.isDraft }
    val completeCount = sightings.count { !it.isDraft }

    // Filter and Sort logic
    val filteredSightings by remember(sightings, searchQuery, selectedCategory, statusFilter, selectedSort) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase(Locale.US)
            val normalizedQuery = PlateOcrEngine.normalizePlate(searchQuery)

            var list = sightings.filter { sighting ->
                val matchesStatus = when (statusFilter) {
                    "draft" -> sighting.isDraft
                    "complete" -> !sighting.isDraft
                    else -> true
                }
                val matchesCategory = selectedCategory == null || sighting.category == selectedCategory
                val matchesSearch = if (query.isBlank()) true else {
                    sighting.brand.lowercase(Locale.US).contains(query) ||
                            sighting.model.lowercase(Locale.US).contains(query) ||
                            sighting.locationName.lowercase(Locale.US).contains(query) ||
                            sighting.area.lowercase(Locale.US).contains(query) ||
                            sighting.city.lowercase(Locale.US).contains(query) ||
                            sighting.numberPlateRaw.lowercase(Locale.US).contains(query) ||
                            (normalizedQuery.isNotBlank() && sighting.numberPlateNormalized.contains(normalizedQuery))
                }
                matchesStatus && matchesCategory && matchesSearch
            }

            list = when (selectedSort) {
                SightingSort.NEWEST -> list.sortedByDescending { it.sightingDate + it.sightingTime }
                SightingSort.OLDEST -> list.sortedBy { it.sightingDate + it.sightingTime }
                SightingSort.BRAND_AZ -> list.sortedBy { it.brand.lowercase(Locale.US) }
                SightingSort.BRAND_ZA -> list.sortedByDescending { it.brand.lowercase(Locale.US) }
            }

            list
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlack)
            .padding(top = 16.dp)
    ) {
        // TOP HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SHARED FLEET",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = SpotrGreen
                )
                Text(
                    text = garageName.ifBlank { "Private Garage" },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = TextWhite
                )
            }

            // Sort Button with dropdown
            Box {
                Button(
                    onClick = { showSortMenu = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CharcoalCard,
                        contentColor = TextWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.border(1.dp, CharcoalBorder, RoundedCornerShape(8.dp))
                ) {
                    Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort", modifier = Modifier.size(16.dp), tint = SpotrGreen)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(selectedSort.label, fontSize = 12.sp)
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(CharcoalCard)
                ) {
                    SightingSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label, color = if (selectedSort == sort) SpotrGreen else TextWhite) },
                            onClick = {
                                selectedSort = sort
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SEARCH BAR
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search brand, model, plate, or city...", color = TextMuted, fontSize = 14.sp) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = SpotrGreen)
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SpotrGreen,
                unfocusedBorderColor = CharcoalBorder,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedContainerColor = CharcoalCard,
                unfocusedContainerColor = CharcoalCard
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("garage_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // CATEGORY FILTER CHIPS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. ALL
            val isAll = statusFilter == "all" && selectedCategory == null
            FilterChip(
                selected = isAll,
                onClick = {
                    statusFilter = "all"
                    selectedCategory = null
                },
                label = { Text("All (${sightings.size})", fontSize = 12.sp) },
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

            // 2. INCOMPLETE / DRAFTS FILTER CHIP
            val isDrafts = statusFilter == "draft"
            FilterChip(
                selected = isDrafts,
                onClick = { statusFilter = if (isDrafts) "all" else "draft" },
                label = { Text("Incomplete ($draftCount)", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WarningAmber,
                    selectedLabelColor = NearBlack,
                    containerColor = CharcoalCard,
                    labelColor = if (draftCount > 0) WarningAmber else TextWhite
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isDrafts,
                    borderColor = if (draftCount > 0) WarningAmber.copy(alpha = 0.6f) else CharcoalBorder,
                    selectedBorderColor = WarningAmber
                )
            )

            // 3. COMPLETED SIGHTINGS CHIP
            val isComplete = statusFilter == "complete"
            FilterChip(
                selected = isComplete,
                onClick = { statusFilter = if (isComplete) "all" else "complete" },
                label = { Text("Completed ($completeCount)", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpotrGreen,
                    selectedLabelColor = NearBlack,
                    containerColor = CharcoalCard,
                    labelColor = TextWhite
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isComplete,
                    borderColor = CharcoalBorder,
                    selectedBorderColor = SpotrGreen
                )
            )

            VehicleCategory.entries.forEach { cat ->
                val count = sightings.count { it.category == cat.label }
                if (count > 0 || cat == VehicleCategory.SUPERCAR || cat == VehicleCategory.SPORTS_CAR) {
                    val isSelected = selectedCategory == cat.label
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = if (isSelected) null else cat.label },
                        label = { Text("${cat.label} ($count)", fontSize = 12.sp) },
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
        }

        Spacer(modifier = Modifier.height(12.dp))

        // VEHICLE SIGHTINGS GRID
        if (filteredSightings.isEmpty()) {
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
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedCategory != null) "No matching sightings found" else "Garage is empty",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "Spot exotic supercars, luxury cars, or bikes and log them to your private garage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    Button(
                        onClick = onAddSightingClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotrGreen,
                            contentColor = NearBlack
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add First Sighting", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredSightings, key = { it.id }) { sighting ->
                    VehicleCard(
                        sighting = sighting,
                        onClick = { onSightingClick(sighting) }
                    )
                }
            }
        }
    }
}

@Composable
fun VehicleCard(
    sighting: Sighting,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalCard)
            .border(
                if (sighting.isDraft) 1.5.dp else 1.dp,
                if (sighting.isDraft) WarningAmber else CharcoalBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        // Thumbnail Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(CarbonDark)
        ) {
            if (sighting.primaryImagePath.isNotBlank()) {
                AsyncImage(
                    model = sighting.primaryImagePath,
                    contentDescription = "${sighting.brand} ${sighting.model}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = TextDim, modifier = Modifier.size(40.dp))
                }
            }

            // Category tag
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NearBlack.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = sighting.category.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotrGreen
                )
            }

            // Status, sync indicator, or number of photos
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SyncStatusBadge(syncStatus = sighting.syncStatus, compact = true)
                if (sighting.isDraft) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NearBlack.copy(alpha = 0.9f))
                            .border(0.5.dp, WarningAmber, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "DRAFT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber
                        )
                    }
                } else if (sighting.imagePaths.size > 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NearBlack.copy(alpha = 0.85f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "+${sighting.imagePaths.size}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }
                }
            }
        }

        // Card Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            val brandDisplay = if (sighting.brand.isNotBlank()) sighting.brand.uppercase() else if (sighting.isDraft) "UNNAMED DRAFT" else "CAR"
            Text(
                text = brandDisplay,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = if (sighting.isDraft && sighting.brand.isBlank()) WarningAmber else SpotrGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = sighting.model.ifBlank { if (sighting.isDraft) "Tap to complete details" else "Vehicle" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (sighting.model.isBlank()) TextDim else TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Missing fields chips for drafts
            if (sighting.isDraft && sighting.missingFields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sighting.missingFields.take(2).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(CarbonDark)
                                .border(0.5.dp, WarningAmber.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(tag, fontSize = 8.sp, color = WarningAmber, maxLines = 1)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Number plate if present
            if (sighting.numberPlateRaw.isNotBlank() || sighting.numberPlateNormalized.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CarbonDark)
                        .border(0.5.dp, CharcoalBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sighting.numberPlateRaw.ifBlank { sighting.numberPlateNormalized },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Location & Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = sighting.area.ifBlank { sighting.city }.ifBlank { sighting.sightingDate },
                    fontSize = 11.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
