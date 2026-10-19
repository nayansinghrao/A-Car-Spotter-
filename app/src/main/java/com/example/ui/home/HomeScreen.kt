package com.example.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Garage
import com.example.data.model.Sighting
import com.example.data.model.VehicleCategory
import com.example.ui.theme.AccentCyan
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

@Composable
fun HomeScreen(
    garage: Garage?,
    sightings: List<Sighting>,
    currentUserId: String,
    onNavigateToGarage: () -> Unit,
    onNavigateToAddSighting: () -> Unit,
    onSightingClick: (Sighting) -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current

    val totalSightings = sightings.size
    val supercarsCount = sightings.count { it.category == VehicleCategory.SUPERCAR.label }
    val bikesCount = sightings.count { VehicleCategory.fromLabel(it.category).isBike }
    val topBrand = sightings.groupingBy { it.brand }.eachCount().maxByOrNull { it.value }?.key ?: "None"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // TOP BAR / GARAGE HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SpotrGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SPOTR DASHBOARD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = SpotrGreen
                    )
                }
                Text(
                    text = garage?.name ?: "Private Garage",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = TextWhite
                )
            }

            // Quick Add Icon Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SpotrGreen)
                    .clickable(onClick = onNavigateToAddSighting),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Sighting",
                    tint = NearBlack,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // 2-PERSON GARAGE STATUS BANNER
        if (garage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Group, contentDescription = null, tint = SpotrGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FLEET CREW (${garage.memberUids.size}/2)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = TextWhite
                            )
                        }

                        // Invite code pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CarbonDark)
                                .border(1.dp, CharcoalBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("SPOTR Invite Code", garage.inviteCode)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Code copied: ${garage.inviteCode}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = garage.inviteCode,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = SpotrGreen
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextMuted, modifier = Modifier.size(12.dp))
                        }
                    }

                    if (garage.memberUids.size < 2) {
                        Text(
                            text = "Slot open! Share code '${garage.inviteCode}' with your partner. You must approve their request in Profile to grant access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber
                        )
                    } else {
                        Text(
                            text = "Garage full with 2 authorized spotters. All logs sync in real time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotrGreen
                        )
                    }
                }
            }
        }

        // QUICK STAT METRICS (2x2 Grid)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "TOTAL SPOTTED",
                    value = "$totalSightings",
                    sub = "Vehicles in fleet",
                    icon = Icons.Default.DirectionsCar,
                    accent = SpotrGreen,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "SUPERCARS",
                    value = "$supercarsCount",
                    sub = "Exotics logged",
                    icon = Icons.Default.Speed,
                    accent = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "BIKES & SUPERS",
                    value = "$bikesCount",
                    sub = "Two-wheelers",
                    icon = Icons.Default.TwoWheeler,
                    accent = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "TOP BRAND",
                    value = topBrand.take(12),
                    sub = "Most frequently spotted",
                    icon = Icons.Default.Speed,
                    accent = TextWhite,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // RECENT SIGHTINGS CAROUSEL
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LATEST SPOTTINGS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = TextWhite
                )

                Text(
                    text = "VIEW GARAGE >",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SpotrGreen,
                    modifier = Modifier.clickable(onClick = onNavigateToGarage)
                )
            }

            if (sightings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalCard)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No vehicles spotted yet", color = TextWhite, fontWeight = FontWeight.Bold)
                        Text("Tap + to add your first supercar or exotic sighting.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                        Button(
                            onClick = onNavigateToAddSighting,
                            colors = ButtonDefaults.buttonColors(containerColor = SpotrGreen, contentColor = NearBlack),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Spot a Vehicle", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    sightings.take(5).forEach { sighting ->
                        RecentCard(sighting = sighting, onClick = { onSightingClick(sighting) })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    sub: String,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalCard)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    ),
                    color = TextMuted
                )
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = accent
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = TextDim
            )
        }
    }
}

@Composable
private fun RecentCard(
    sighting: Sighting,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalCard)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(CarbonDark)
        ) {
            if (sighting.primaryImagePath.isNotBlank()) {
                AsyncImage(
                    model = sighting.primaryImagePath,
                    contentDescription = "${sighting.brand} ${sighting.model}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NearBlack.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(sighting.category.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SpotrGreen)
            }
        }

        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = sighting.brand.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = SpotrGreen,
                maxLines = 1
            )
            Text(
                text = sighting.model,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sighting.sightingDate,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}
