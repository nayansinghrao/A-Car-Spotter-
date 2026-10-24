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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PendingActions
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
import com.example.ui.components.SyncStatusBadge
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
    onResumeDraft: (Sighting) -> Unit = {},
    onNavigateToProfile: () -> Unit,
    onSignInClick: () -> Unit = {}
) {
    val context = LocalContext.current

    val draftSightings = sightings.filter { it.isDraft }
    val completedSightings = sightings.filter { !it.isDraft }
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
        } else {
            // CLOUD SYNC NOT CONNECTED BANNER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(WarningAmber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CLOUD SYNC NOT CONNECTED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = WarningAmber
                        )
                    }
                    Text(
                        text = "Sign in with Google to enable real-time Cloud Firestore persistence, sync your vehicle sightings safely, and invite a friend to your shared fleet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Button(
                        onClick = onSignInClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotrGreen, contentColor = NearBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("Sign In with Google", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // DEDICATED INCOMPLETE DRAFTS BANNER
        if (draftSightings.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.5.dp, WarningAmber, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(WarningAmber)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "INCOMPLETE DRAFTS (${draftSightings.size})",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                ),
                                color = WarningAmber
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(WarningAmber.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "ACTION REQUIRED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber
                            )
                        }
                    }

                    Text(
                        text = "Saved partial records missing photos, plates, or details. Tap any draft below to resume and complete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        draftSightings.forEach { draft ->
                            DraftSightingCard(
                                draft = draft,
                                onResume = { onResumeDraft(draft) }
                            )
                        }
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
                    title = "INCOMPLETE DRAFTS",
                    value = "${draftSightings.size}",
                    sub = if (draftSightings.isEmpty()) "All completed" else "Pending completion",
                    icon = Icons.Default.PendingActions,
                    accent = if (draftSightings.isEmpty()) SpotrGreen else WarningAmber,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "SUPERCARS",
                    value = "$supercarsCount",
                    sub = "Exotics logged",
                    icon = Icons.Default.Speed,
                    accent = AccentCyan,
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

            SyncStatusBadge(
                syncStatus = sighting.syncStatus,
                compact = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
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

@Composable
private fun DraftSightingCard(
    draft: Sighting,
    onResume: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonDark)
            .border(1.dp, WarningAmber.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .clickable(onClick = onResume)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(95.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CharcoalCard)
        ) {
            if (draft.primaryImagePath.isNotBlank()) {
                AsyncImage(
                    model = draft.primaryImagePath,
                    contentDescription = "Draft thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SyncStatusBadge(
                    syncStatus = draft.syncStatus,
                    compact = true
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NearBlack.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("DRAFT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WarningAmber)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val titleText = if (draft.brand.isNotBlank() || draft.model.isNotBlank()) {
            "${draft.brand.ifBlank { "Unbranded" }} ${draft.model}".trim().take(22).uppercase()
        } else {
            "UNNAMED DRAFT"
        }

        Text(
            text = titleText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = TextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val plateText = draft.numberPlateRaw.ifBlank { draft.numberPlateNormalized }
        Text(
            text = if (plateText.isNotBlank()) plateText else "Plate unconfirmed",
            style = MaterialTheme.typography.labelSmall,
            color = if (plateText.isNotBlank()) SpotrGreen else TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        val missing = draft.missingFields
        if (missing.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                missing.take(2).forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(CharcoalCard)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(tag, fontSize = 8.sp, color = WarningAmber)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onResume,
            colors = ButtonDefaults.buttonColors(
                containerColor = SpotrGreen,
                contentColor = NearBlack
            ),
            shape = RoundedCornerShape(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp, horizontal = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        ) {
            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Resume", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

