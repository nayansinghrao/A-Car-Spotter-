package com.example.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Garage
import com.example.data.model.JoinRequest
import com.example.data.model.UserProfile
import com.example.ui.diagnostic.DiagnosticLogsDialog
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

@Composable
fun ProfileScreen(
    userProfile: UserProfile?,
    garage: Garage?,
    pendingJoinRequests: List<JoinRequest>,
    currentUserId: String,
    onApproveRequest: (JoinRequest) -> Unit,
    onRejectRequest: (JoinRequest) -> Unit,
    onSwitchGarageClick: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val isOwner = garage?.isOwner(currentUserId) == true
    var showDiagnostics by remember { mutableStateOf(false) }

    if (showDiagnostics) {
        DiagnosticLogsDialog(onDismissRequest = { showDiagnostics = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = SpotrGreen,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ACCOUNT & FLEET ACCESS",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = TextWhite
            )
        }

        // USER IDENTITY CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CharcoalCard)
                .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(SpotrGreenContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = SpotrOnGreenContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = userProfile?.displayName?.ifBlank { "Spotter" } ?: "Spotter",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = userProfile?.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Text(
                        text = "UID: ${currentUserId.take(10)}...",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextDim
                    )
                }
            }
        }

        // ACTIVE GARAGE CARD
        if (garage != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ACTIVE GARAGE",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = SpotrGreen
                        )
                        Text(
                            text = garage.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = TextWhite
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isOwner) SpotrGreenContainer else CharcoalSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isOwner) "OWNER" else "MEMBER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOwner) SpotrOnGreenContainer else TextWhite
                        )
                    }
                }

                // INVITE CODE WITH COPY
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CarbonDark)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("SPOTR Invite Code", garage.inviteCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Invite code copied: ${garage.inviteCode}", Toast.LENGTH_SHORT).show()
                        }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Garage Invite Code (2nd Member)", fontSize = 11.sp, color = TextMuted)
                            Text(
                                text = garage.inviteCode,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = SpotrGreen
                            )
                        }
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy code", tint = SpotrGreen)
                    }
                }

                // MEMBERS LIST
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "MEMBERS (${garage.memberUids.size}/2)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = TextWhite
                    )

                    // Owner row
                    MemberItem(
                        title = if (isOwner) "You (${garage.ownerEmail})" else garage.ownerEmail,
                        role = "Owner",
                        isMe = isOwner
                    )

                    // Second Member row
                    if (garage.memberUids.size >= 2) {
                        val secondUid = garage.memberUids.firstOrNull { it != garage.ownerUid } ?: ""
                        MemberItem(
                            title = if (secondUid == currentUserId) "You (Co-Spotter)" else "Co-Spotter Member",
                            role = "Member",
                            isMe = secondUid == currentUserId
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CarbonDark)
                                .border(1.dp, WarningAmber, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Slot 2 is open. Give your invite code to your co-spotter. When they request to join, approve them below.",
                                fontSize = 12.sp,
                                color = WarningAmber
                            )
                        }
                    }
                }
            }

            // PENDING JOIN REQUESTS (OWNER APPROVAL FLOW)
            if (isOwner) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CharcoalCard)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "PENDING JOIN REQUESTS (${pendingJoinRequests.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = WarningAmber
                    )

                    if (pendingJoinRequests.isEmpty()) {
                        Text(
                            text = "No pending join requests.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        pendingJoinRequests.forEach { req ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CarbonDark)
                                    .border(1.dp, CharcoalBorder, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column {
                                        Text(
                                            text = req.displayName.ifBlank { "User" },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = TextWhite
                                        )
                                        Text(text = req.email, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                        Text(
                                            text = "Submitted code: ${req.inviteCodeSubmitted}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = SpotrGreen
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { onApproveRequest(req) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SpotrGreen,
                                                contentColor = NearBlack
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Approve", fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { onRejectRequest(req) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = DangerContainer,
                                                contentColor = DangerRed
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reject")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SWITCH OR CREATE ANOTHER GARAGE
        Button(
            onClick = onSwitchGarageClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = CharcoalCard,
                contentColor = TextWhite
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
        ) {
            Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, tint = SpotrGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Switch or Join Another Garage")
        }

        // SYSTEM HEALTH & DIAGNOSTICS BUTTON
        OutlinedButton(
            onClick = { showDiagnostics = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                .testTag("profile_diagnostics_button")
        ) {
            Icon(imageVector = Icons.Default.BugReport, contentDescription = null, tint = SpotrGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text("System Health & Diagnostic Logs", fontWeight = FontWeight.SemiBold)
        }

        // SIGN OUT BUTTON
        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(
                containerColor = CarbonDark,
                contentColor = DangerRed
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .testTag("sign_out_button")
        ) {
            Icon(imageVector = Icons.Default.Logout, contentDescription = null, tint = DangerRed)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MemberItem(
    title: String,
    role: String,
    isMe: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CarbonDark)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isMe) SpotrGreen else TextMuted)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, fontSize = 13.sp, color = TextWhite)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(CharcoalSurfaceVariant)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = role, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SpotrGreen)
        }
    }
}
