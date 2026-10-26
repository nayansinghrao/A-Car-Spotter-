package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.auth.SignInScreen
import com.example.ui.navigation.SpotrAppScaffold
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NearBlack
import com.example.ui.theme.SpotrGreen
import com.example.ui.theme.TextWhite
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

                    when {
                        // 1. Initial asynchronous session restoration and garage loading
                        uiState.isAuthLoading || (uiState.currentUser != null && uiState.isGarageLoading && uiState.currentGarage == null) -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(NearBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = "SPOTR",
                                        tint = SpotrGreen,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    CircularProgressIndicator(
                                        color = SpotrGreen,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "SPOTR",
                                        color = TextWhite,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 4.sp,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }

                        // 2. Unauthenticated: Google Sign-In with real error diagnosis & Email fallback
                        uiState.currentUser == null -> {
                            SignInScreen(
                                isLoading = uiState.isAuthLoading,
                                errorMessage = uiState.authErrorMessage,
                                onSignInClick = { viewModel.signInWithGoogle(this@MainActivity) },
                                onEmailSignIn = { email, pass -> viewModel.signInWithEmail(email, pass) },
                                onEmailRegister = { email, pass, name -> viewModel.registerWithEmail(email, pass, name) }
                            )
                        }

                        // 3. Authenticated: Shared private garage interface
                        else -> {
                            SpotrAppScaffold(
                                currentUserId = uiState.currentUser?.uid.orEmpty(),
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
    }
}
