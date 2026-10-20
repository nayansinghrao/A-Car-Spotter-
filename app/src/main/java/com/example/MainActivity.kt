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
import com.example.util.DiagnosticLogger

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DiagnosticLogger.runStartupDiagnostics(this)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NearBlack
                ) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                    // Direct access to main spotting interface
                    SpotrAppScaffold(
                        currentUserId = uiState.currentUser?.uid ?: MainViewModel.LOCAL_USER_ID,
                        userProfile = uiState.userProfile,
                        garage = uiState.currentGarage,
                        sightings = uiState.sightings,
                        pendingJoinRequests = uiState.pendingJoinRequests,
                        pendingRequestGarage = uiState.pendingRequestGarage,
                        isGarageLoading = uiState.isGarageLoading,
                        garageErrorMessage = uiState.garageErrorMessage,
                        autoSaveStatus = uiState.autoSaveStatus,
                        onDraftChanged = { sighting -> viewModel.onDraftFieldsChanged(sighting) },
                        onCreateGarage = { name -> viewModel.createGarage(name) },
                        onJoinGarage = { code -> viewModel.joinGarageWithCode(code) },
                        onCheckPendingStatus = { viewModel.checkPendingApproval() },
                        onApproveRequest = { req -> viewModel.approveJoinRequest(req) },
                        onRejectRequest = { req -> viewModel.rejectJoinRequest(req) },
                        onSaveSighting = { sighting, isComplete -> viewModel.saveSightingExplicitly(sighting, isComplete) },
                        onDeleteSighting = { id -> viewModel.deleteSighting(id) },
                        onSignOut = { viewModel.signOut() },
                        onSignInClick = { viewModel.signInWithGoogle(this@MainActivity) }
                    )
                }
            }
        }
    }
}
