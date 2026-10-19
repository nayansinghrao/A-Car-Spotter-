package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Garage
import com.example.data.model.JoinRequest
import com.example.data.model.Sighting
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.data.repository.GarageRepository
import com.example.data.repository.SightingRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val currentUser: FirebaseUser? = null,
    val userProfile: UserProfile? = null,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,

    val currentGarage: Garage? = null,
    val pendingJoinRequests: List<JoinRequest> = emptyList(),
    val pendingRequestGarage: Garage? = null,
    val isGarageLoading: Boolean = false,
    val garageErrorMessage: String? = null,

    val sightings: List<Sighting> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = AuthRepository(application)
    private val garageRepo = GarageRepository(application)
    private val sightingRepo = SightingRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var garageObserveJob: Job? = null
    private var joinRequestsObserveJob: Job? = null
    private var sightingsObserveJob: Job? = null

    init {
        // Observe Firebase Auth state
        viewModelScope.launch {
            authRepo.observeAuthState().collect { user ->
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    isAuthLoading = false
                )
                if (user != null) {
                    loadUserData(user.uid)
                } else {
                    resetGarageState()
                }
            }
        }
    }

    private fun loadUserData(uid: String) {
        viewModelScope.launch {
            val profile = authRepo.getUserProfile(uid)
            _uiState.value = _uiState.value.copy(userProfile = profile)
            val garageId = profile?.currentGarageId.orEmpty()
            if (garageId.isNotBlank()) {
                observeActiveGarage(garageId)
            }
        }
    }

    private fun observeActiveGarage(garageId: String) {
        garageObserveJob?.cancel()
        joinRequestsObserveJob?.cancel()
        sightingsObserveJob?.cancel()

        // 1. Observe Garage Doc
        garageObserveJob = viewModelScope.launch {
            garageRepo.observeGarage(garageId).collect { garage ->
                _uiState.value = _uiState.value.copy(currentGarage = garage)
            }
        }

        // 2. Observe Join Requests (for owner approval)
        joinRequestsObserveJob = viewModelScope.launch {
            garageRepo.observeJoinRequests(garageId).collect { requests ->
                _uiState.value = _uiState.value.copy(pendingJoinRequests = requests)
            }
        }

        // 3. Observe Sightings
        sightingsObserveJob = viewModelScope.launch {
            sightingRepo.observeSightings(garageId).collect { sightingsList ->
                _uiState.value = _uiState.value.copy(sightings = sightingsList)
            }
        }
    }

    private fun resetGarageState() {
        garageObserveJob?.cancel()
        joinRequestsObserveJob?.cancel()
        sightingsObserveJob?.cancel()
        _uiState.value = _uiState.value.copy(
            currentGarage = null,
            pendingJoinRequests = emptyList(),
            pendingRequestGarage = null,
            sightings = emptyList()
        )
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
            val result = authRepo.signInWithGoogle()
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                val message = if (err is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    "Sign-in was cancelled. Please choose a Google account to continue."
                } else {
                    err?.localizedMessage ?: "Failed to sign in with Google"
                }
                _uiState.value = _uiState.value.copy(isAuthLoading = false, authErrorMessage = message)
            }
        }
    }

    fun signOut() {
        authRepo.signOut()
        resetGarageState()
    }

    fun createGarage(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGarageLoading = true, garageErrorMessage = null)
            val result = garageRepo.createGarage(name)
            _uiState.value = _uiState.value.copy(isGarageLoading = false)
            if (result.isSuccess) {
                val garage = result.getOrNull()
                if (garage != null) {
                    _uiState.value = _uiState.value.copy(currentGarage = garage)
                    observeActiveGarage(garage.id)
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    garageErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to create garage"
                )
            }
        }
    }

    fun joinGarageWithCode(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGarageLoading = true, garageErrorMessage = null)
            val targetGarage = garageRepo.findGarageByInviteCode(code)
            if (targetGarage == null) {
                _uiState.value = _uiState.value.copy(
                    isGarageLoading = false,
                    garageErrorMessage = "No garage found with invite code '$code'. Check the code and try again."
                )
                return@launch
            }

            if (targetGarage.isFull) {
                _uiState.value = _uiState.value.copy(
                    isGarageLoading = false,
                    garageErrorMessage = "This garage already has 2 members. Cannot join."
                )
                return@launch
            }

            val requestResult = garageRepo.requestToJoinGarage(targetGarage.id, code)
            _uiState.value = _uiState.value.copy(isGarageLoading = false)
            if (requestResult.isSuccess) {
                _uiState.value = _uiState.value.copy(pendingRequestGarage = targetGarage)
            } else {
                _uiState.value = _uiState.value.copy(
                    garageErrorMessage = requestResult.exceptionOrNull()?.localizedMessage ?: "Failed to submit request"
                )
            }
        }
    }

    fun checkPendingApproval() {
        val user = _uiState.value.currentUser ?: return
        val pendingGarage = _uiState.value.pendingRequestGarage ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGarageLoading = true)
            val profile = authRepo.getUserProfile(user.uid)
            _uiState.value = _uiState.value.copy(userProfile = profile, isGarageLoading = false)
            if (profile?.currentGarageId == pendingGarage.id) {
                _uiState.value = _uiState.value.copy(pendingRequestGarage = null)
                observeActiveGarage(pendingGarage.id)
            }
        }
    }

    fun approveJoinRequest(request: JoinRequest) {
        val garage = _uiState.value.currentGarage ?: return
        viewModelScope.launch {
            val result = garageRepo.approveJoinRequest(garage, request)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    garageErrorMessage = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun rejectJoinRequest(request: JoinRequest) {
        val garage = _uiState.value.currentGarage ?: return
        viewModelScope.launch {
            garageRepo.rejectJoinRequest(garage.id, request.uid)
        }
    }

    suspend fun saveSighting(sighting: Sighting): Result<String> {
        val garageId = _uiState.value.currentGarage?.id ?: return Result.failure(IllegalStateException("No active garage"))
        return sightingRepo.addSighting(garageId, sighting)
    }

    fun deleteSighting(sightingId: String) {
        val garageId = _uiState.value.currentGarage?.id ?: return
        viewModelScope.launch {
            sightingRepo.deleteSighting(garageId, sightingId)
        }
    }
}
