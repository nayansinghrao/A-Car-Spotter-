package com.example.ui.garage_setup

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Garage
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalSurfaceVariant
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NearBlack
import com.example.ui.theme.SpotrGreen
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WarningAmber

@Composable
fun GarageSetupScreen(
    userEmail: String,
    isLoading: Boolean,
    errorMessage: String?,
    pendingRequestGarage: Garage?,
    onCreateGarage: (name: String) -> Unit,
    onJoinGarage: (code: String) -> Unit,
    onCheckPendingStatus: () -> Unit,
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var garageName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlack)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Brand Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = SpotrGreen,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SPOTR GARAGE SETUP",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = TextWhite
                )
            }

            Text(
                text = if (userEmail.isNotBlank()) "Signed in as $userEmail" else "Signed in with Google",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Pending Request Banner
            if (pendingRequestGarage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CarbonDark)
                        .border(1.dp, WarningAmber, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = WarningAmber,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Join Request Pending",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = WarningAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You requested to join '${pendingRequestGarage.name}'. The garage owner (${pendingRequestGarage.ownerEmail}) must approve your membership.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onCheckPendingStatus,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarningAmber,
                                contentColor = NearBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check If Approved")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Tabs: Create vs Join
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CarbonDark,
                contentColor = SpotrGreen,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = SpotrGreen
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Create Garage",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) SpotrGreen else TextMuted
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Join with Code",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) SpotrGreen else TextMuted
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tab Content
            if (selectedTab == 0) {
                // Create Garage
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CharcoalCard)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Create Your Private Garage",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "You will be the garage owner. You will receive a unique invite code to share with your chosen second member.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = garageName,
                        onValueChange = { garageName = it },
                        label = { Text("Garage Name (e.g. Apex Spotters)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotrGreen,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = CarbonDark,
                            unfocusedContainerColor = CarbonDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("garage_name_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { if (garageName.isNotBlank()) onCreateGarage(garageName) },
                        enabled = !isLoading && garageName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotrGreen,
                            contentColor = NearBlack
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_garage_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NearBlack)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.AddBusiness, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create & Get Invite Code", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Join Garage
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CharcoalCard)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Enter Invite Code",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "Enter the 6-character code provided by the garage owner (e.g. SPT-4K9). The owner must approve your join request.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.uppercase() },
                        label = { Text("Invite Code (e.g. SPT-4K9)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotrGreen,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = CarbonDark,
                            unfocusedContainerColor = CarbonDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invite_code_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { if (inviteCode.isNotBlank()) onJoinGarage(inviteCode) },
                        enabled = !isLoading && inviteCode.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotrGreen,
                            contentColor = NearBlack
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_join_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NearBlack)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.GroupAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Request To Join Garage", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = DangerRed,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onSignOut) {
                Text(
                    text = if (userEmail.isNotBlank()) "Sign out of $userEmail" else "Sign out",
                    color = TextDim,
                    fontSize = 13.sp
                )
            }
        }
    }
}
