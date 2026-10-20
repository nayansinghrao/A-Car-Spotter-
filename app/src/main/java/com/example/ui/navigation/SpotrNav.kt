package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Garage
import com.example.data.model.JoinRequest
import com.example.data.model.Sighting
import com.example.data.model.UserProfile
import com.example.ui.AutoSaveStatus
import com.example.ui.garage.GarageScreen
import com.example.ui.garage_setup.GarageSetupScreen
import com.example.ui.home.HomeScreen
import com.example.ui.map.MapScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.sighting.AddSightingScreen
import com.example.ui.sighting.SightingDetailScreen
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.NearBlack
import com.example.ui.theme.SpotrGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

enum class SpotrTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    GARAGE("Garage", Icons.Default.DirectionsCar),
    MAP("Map", Icons.Default.LocationOn),
    PROFILE("Profile", Icons.Default.Person)
}

sealed class ScreenState {
    data class TabView(val tab: SpotrTab) : ScreenState()
    data class AddSighting(val initialSighting: Sighting? = null) : ScreenState()
    data class SightingDetail(val sighting: Sighting) : ScreenState()
    data object GarageSetup : ScreenState()
}

@Composable
fun SpotrAppScaffold(
    currentUserId: String,
    userProfile: UserProfile?,
    garage: Garage?,
    sightings: List<Sighting>,
    pendingJoinRequests: List<JoinRequest>,
    pendingRequestGarage: Garage?,
    isGarageLoading: Boolean,
    garageErrorMessage: String?,
    autoSaveStatus: AutoSaveStatus = AutoSaveStatus.IDLE,
    onDraftChanged: (Sighting) -> Unit = {},
    onCreateGarage: (name: String) -> Unit,
    onJoinGarage: (code: String) -> Unit,
    onCheckPendingStatus: () -> Unit,
    onApproveRequest: (JoinRequest) -> Unit,
    onRejectRequest: (JoinRequest) -> Unit,
    onSaveSighting: suspend (Sighting, Boolean) -> Result<String>,
    onDeleteSighting: (sightingId: String) -> Unit,
    onSignOut: () -> Unit,
    onSignInClick: () -> Unit = {}
) {
    var screenState by remember {
        mutableStateOf<ScreenState>(
            if (garage == null) ScreenState.GarageSetup else ScreenState.TabView(SpotrTab.HOME)
        )
    }

    // Keep screen state in sync if garage changes
    if (garage == null && screenState !is ScreenState.GarageSetup) {
        screenState = ScreenState.GarageSetup
    } else if (garage != null && screenState is ScreenState.GarageSetup) {
        screenState = ScreenState.TabView(SpotrTab.HOME)
    }

    // Handle Android system back button
    BackHandler(enabled = screenState !is ScreenState.TabView || (screenState as ScreenState.TabView).tab != SpotrTab.HOME) {
        when (val state = screenState) {
            is ScreenState.AddSighting -> screenState = ScreenState.TabView(SpotrTab.GARAGE)
            is ScreenState.SightingDetail -> screenState = ScreenState.TabView(SpotrTab.GARAGE)
            is ScreenState.GarageSetup -> {
                if (garage != null) screenState = ScreenState.TabView(SpotrTab.HOME)
            }
            is ScreenState.TabView -> {
                if (state.tab != SpotrTab.HOME) {
                    screenState = ScreenState.TabView(SpotrTab.HOME)
                }
            }
        }
    }

    val showBarsAndFab = screenState is ScreenState.TabView && garage != null

    Scaffold(
        containerColor = NearBlack,
        floatingActionButton = {
            AnimatedVisibility(
                visible = showBarsAndFab,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(
                    onClick = { screenState = ScreenState.AddSighting() },
                    containerColor = SpotrGreen,
                    contentColor = NearBlack,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .size(58.dp)
                        .testTag("add_sighting_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Sighting",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        bottomBar = {
            if (showBarsAndFab) {
                val currentTab = (screenState as ScreenState.TabView).tab
                NavigationBar(
                    containerColor = CharcoalCard,
                    contentColor = TextWhite,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .border(1.dp, CharcoalBorder)
                        .testTag("bottom_navigation_bar")
                ) {
                    SpotrTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { screenState = ScreenState.TabView(tab) },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NearBlack,
                                selectedTextColor = SpotrGreen,
                                indicatorColor = SpotrGreen,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = screenState) {
                is ScreenState.GarageSetup -> {
                    GarageSetupScreen(
                        userEmail = userProfile?.email ?: "",
                        isLoading = isGarageLoading,
                        errorMessage = garageErrorMessage,
                        pendingRequestGarage = pendingRequestGarage,
                        onCreateGarage = onCreateGarage,
                        onJoinGarage = onJoinGarage,
                        onCheckPendingStatus = onCheckPendingStatus,
                        onSignOut = onSignOut
                    )
                }

                is ScreenState.AddSighting -> {
                    AddSightingScreen(
                        garageId = garage?.id ?: "",
                        initialSighting = state.initialSighting,
                        autoSaveStatus = autoSaveStatus,
                        onDraftChanged = onDraftChanged,
                        onNavigateBack = { screenState = ScreenState.TabView(SpotrTab.GARAGE) },
                        onSaveSuccess = { screenState = ScreenState.TabView(SpotrTab.GARAGE) },
                        onSaveSighting = onSaveSighting
                    )
                }

                is ScreenState.SightingDetail -> {
                    SightingDetailScreen(
                        sighting = state.sighting,
                        currentUserId = currentUserId,
                        isOwner = garage?.isOwner(currentUserId) == true,
                        onNavigateBack = { screenState = ScreenState.TabView(SpotrTab.GARAGE) },
                        onResumeDraft = { draft -> screenState = ScreenState.AddSighting(draft) },
                        onDeleteConfirmed = {
                            onDeleteSighting(state.sighting.id)
                            screenState = ScreenState.TabView(SpotrTab.GARAGE)
                        }
                    )
                }

                is ScreenState.TabView -> {
                    when (state.tab) {
                        SpotrTab.HOME -> {
                            HomeScreen(
                                garage = garage,
                                sightings = sightings,
                                currentUserId = currentUserId,
                                onNavigateToGarage = { screenState = ScreenState.TabView(SpotrTab.GARAGE) },
                                onNavigateToAddSighting = { screenState = ScreenState.AddSighting() },
                                onSightingClick = { sighting -> screenState = ScreenState.SightingDetail(sighting) },
                                onResumeDraft = { draft -> screenState = ScreenState.AddSighting(draft) },
                                onNavigateToProfile = { screenState = ScreenState.TabView(SpotrTab.PROFILE) }
                            )
                        }

                        SpotrTab.GARAGE -> {
                            GarageScreen(
                                sightings = sightings,
                                garageName = garage?.name ?: "Private Garage",
                                onSightingClick = { sighting -> screenState = ScreenState.SightingDetail(sighting) },
                                onResumeDraft = { draft -> screenState = ScreenState.AddSighting(draft) },
                                onAddSightingClick = { screenState = ScreenState.AddSighting() }
                            )
                        }

                        SpotrTab.MAP -> {
                            MapScreen(
                                sightings = sightings,
                                onSightingClick = { sighting -> screenState = ScreenState.SightingDetail(sighting) }
                            )
                        }

                        SpotrTab.PROFILE -> {
                            ProfileScreen(
                                userProfile = userProfile,
                                garage = garage,
                                pendingJoinRequests = pendingJoinRequests,
                                currentUserId = currentUserId,
                                onApproveRequest = onApproveRequest,
                                onRejectRequest = onRejectRequest,
                                onSwitchGarageClick = { screenState = ScreenState.GarageSetup },
                                onSignOut = onSignOut,
                                onSignInClick = onSignInClick
                            )
                        }
                    }
                }
            }
        }
    }
}
