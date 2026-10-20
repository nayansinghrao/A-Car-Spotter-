package com.example.ui

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AutoSaveStatus {
    IDLE,
    SAVING,
    SAVED,
    FAILED
}

data class MainUiState(
    val currentUser: FirebaseUser? = null,
    val userProfile: UserProfile? = MainViewModel.LOCAL_USER_PROFILE,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,

    val currentGarage: Garage? = MainViewModel.LOCAL_GARAGE,
    val pendingJoinRequests: List<JoinRequest> = emptyList(),
    val pendingRequestGarage: Garage? = null,
    val isGarageLoading: Boolean = false,
    val garageErrorMessage: String? = null,

    val sightings: List<Sighting> = emptyList(),
    val autoSaveStatus: AutoSaveStatus = AutoSaveStatus.IDLE
) {
    val draftSightings: List<Sighting>
        get() = sightings.filter { it.isDraft }

    val completeSightings: List<Sighting>
        get() = sightings.filter { !it.isDraft }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val LOCAL_USER_ID = "local_spotter"
        const val LOCAL_GARAGE_ID = "local_garage"

        val LOCAL_GARAGE = Garage(
            id = LOCAL_GARAGE_ID,
            name = "My Private Garage",
            ownerUid = LOCAL_USER_ID,
            ownerEmail = "local@spotr.app",
            memberUids = listOf(LOCAL_USER_ID),
            inviteCode = "SPT-LOCAL"
        )

        val LOCAL_USER_PROFILE = UserProfile(
            uid = LOCAL_USER_ID,
            displayName = "Spotter",
            email = "local@spotr.app",
            currentGarageId = LOCAL_GARAGE_ID
        )
    }

    private val authRepo = AuthRepository(application)
    private val garageRepo = GarageRepository(application)
    private val sightingRepo = SightingRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var garageObserveJob: Job? = null
    private var joinRequestsObserveJob: Job? = null
    private var sightingsObserveJob: Job? = null
    private var autoSaveJob: Job? = null

    init {
        // Immediately observe local garage so user lands directly on the main spotting interface
        observeActiveGarage(LOCAL_GARAGE_ID)

        // Observe Firebase Auth state in background
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
        autoSaveJob?.cancel()
        _uiState.value = _uiState.value.copy(
            userProfile = LOCAL_USER_PROFILE,
            currentGarage = LOCAL_GARAGE,
            pendingJoinRequests = emptyList(),
            pendingRequestGarage = null,
            autoSaveStatus = AutoSaveStatus.IDLE
        )
        observeActiveGarage(LOCAL_GARAGE_ID)
    }

    fun signInWithGoogle(activityContext: Context? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
            val launchContext = activityContext ?: getApplication<Application>()
            val result = authRepo.signInWithGoogle(launchContext)
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

    /**
     * Debounced auto-save triggers 1500ms after user edits.
     * Prevents overlapping saves and updates the stable record ID.
     */
    fun onDraftFieldsChanged(sighting: Sighting) {
        val garageId = _uiState.value.currentGarage?.id ?: return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1500)
            _uiState.value = _uiState.value.copy(autoSaveStatus = AutoSaveStatus.SAVING)
            val draftSighting = sighting.copy(status = "draft")
            val result = sightingRepo.upsertSighting(garageId, draftSighting)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(autoSaveStatus = AutoSaveStatus.SAVED)
            } else {
                _uiState.value = _uiState.value.copy(autoSaveStatus = AutoSaveStatus.FAILED)
            }
        }
    }

    /**
     * Explicit save action for either Draft or Complete.
     * Cancels any pending auto-save job and commits immediately.
     */
    suspend fun saveSightingExplicitly(sighting: Sighting, isComplete: Boolean): Result<String> {
        val garageId = _uiState.value.currentGarage?.id ?: return Result.failure(IllegalStateException("No active garage"))
        autoSaveJob?.cancel()
        _uiState.value = _uiState.value.copy(autoSaveStatus = AutoSaveStatus.SAVING)
        val targetStatus = if (isComplete) "complete" else "draft"
        val targetSighting = sighting.copy(status = targetStatus)
        val result = sightingRepo.upsertSighting(garageId, targetSighting)
        if (result.isSuccess) {
            _uiState.value = _uiState.value.copy(autoSaveStatus = AutoSaveStatus.SAVED)
        } else {
            _uiState.value = _uiState.value.copy(autoSaveStatus = AutoSaveStatus.FAILED)
        }
        return result
    }

    suspend fun saveSighting(sighting: Sighting): Result<String> {
        return saveSightingExplicitly(sighting, isComplete = sighting.isComplete)
    }

    fun resetAutoSaveStatus() {
        _uiState.value = _uiState.value.copy(autoSaveStatus = AutoSaveStatus.IDLE)
    }

    fun deleteSighting(sightingId: String) {
        val garageId = _uiState.value.currentGarage?.id ?: return
        viewModelScope.launch {
            sightingRepo.deleteSighting(garageId, sightingId)
        }
    }
}
