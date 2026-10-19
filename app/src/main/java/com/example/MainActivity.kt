package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.auth.SignInScreen
import com.example.ui.navigation.SpotrAppScaffold
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NearBlack

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NearBlack
                ) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                    // MANDATORY AUTH GATE: Never display fleet or garage data if user is not authenticated
                    if (uiState.currentUser == null) {
                        SignInScreen(
                            isLoading = uiState.isAuthLoading,
                            errorMessage = uiState.authErrorMessage,
                            onSignInClick = { viewModel.signInWithGoogle() }
                        )
                    } else {
                        SpotrAppScaffold(
                            currentUserId = uiState.currentUser?.uid.orEmpty(),
                            userProfile = uiState.userProfile,
                            garage = uiState.currentGarage,
                            sightings = uiState.sightings,
                            pendingJoinRequests = uiState.pendingJoinRequests,
                            pendingRequestGarage = uiState.pendingRequestGarage,
                            isGarageLoading = uiState.isGarageLoading,
                            garageErrorMessage = uiState.garageErrorMessage,
                            onCreateGarage = { name -> viewModel.createGarage(name) },
                            onJoinGarage = { code -> viewModel.joinGarageWithCode(code) },
                            onCheckPendingStatus = { viewModel.checkPendingApproval() },
                            onApproveRequest = { req -> viewModel.approveJoinRequest(req) },
                            onRejectRequest = { req -> viewModel.rejectJoinRequest(req) },
                            onSaveSighting = { sighting -> viewModel.saveSighting(sighting) },
                            onDeleteSighting = { id -> viewModel.deleteSighting(id) },
                            onSignOut = { viewModel.signOut() }
                        )
                    }
                }
            }
        }
    }
}
