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
    val userProfile: UserProfile? = null,
    val isAuthLoading: Boolean = true,
    val authErrorMessage: String? = null,

    val currentGarage: Garage? = null,
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

    private val authRepo = AuthRepository(application)
    private val garageRepo = GarageRepository(application)
    private val sightingRepo = SightingRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var garageObserveJob: Job? = null
    private var joinRequestsObserveJob: Job? = null
    private var sightingsObserveJob: Job? = null
    private var myJoinRequestObserveJob: Job? = null
    private var autoSaveJob: Job? = null

    init {
        // Immediate check of cached user session
        val initialUser = authRepo.currentUser
        if (initialUser != null) {
            _uiState.value = _uiState.value.copy(
                currentUser = initialUser,
                isAuthLoading = false
            )
            loadUserData(initialUser.uid)
        }

        // Asynchronous listener for Firebase Auth session changes
        viewModelScope.launch {
            authRepo.observeAuthState().collect { user ->
                val prevUser = _uiState.value.currentUser
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    isAuthLoading = false
                )
                if (user != null) {
                    if (prevUser?.uid != user.uid || _uiState.value.currentGarage == null) {
                        loadUserData(user.uid)
                    }
                } else {
                    resetGarageState()
                }
            }
        }
    }

    private fun loadUserData(uid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGarageLoading = true)
            var profile = authRepo.getUserProfile(uid)
            if (profile == null) {
                _uiState.value.currentUser?.let { authRepo.syncUserProfile(it) }
                profile = authRepo.getUserProfile(uid)
            }
            _uiState.value = _uiState.value.copy(userProfile = profile)

            val existingGarage = garageRepo.findUserGarage(uid)
            if (existingGarage != null) {
                _uiState.value = _uiState.value.copy(currentGarage = existingGarage)
                authRepo.updateUserCurrentGarage(existingGarage.id)
                observeActiveGarage(existingGarage.id)
            } else {
                _uiState.value = _uiState.value.copy(currentGarage = null)
            }
            _uiState.value = _uiState.value.copy(isGarageLoading = false)
        }
    }

    private fun observeActiveGarage(garageId: String) {
        garageObserveJob?.cancel()
        joinRequestsObserveJob?.cancel()
        sightingsObserveJob?.cancel()

        // 1. Observe Garage Document
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

        // 3. Observe Vehicle Sightings from Cloud Firestore
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
        myJoinRequestObserveJob?.cancel()
        autoSaveJob?.cancel()
        _uiState.value = _uiState.value.copy(
            userProfile = null,
            currentGarage = null,
            pendingJoinRequests = emptyList(),
            pendingRequestGarage = null,
            sightings = emptyList(),
            autoSaveStatus = AutoSaveStatus.IDLE
        )
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

    fun signInWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
            val result = authRepo.signInWithEmail(email, pass)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isAuthLoading = false,
                    authErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to sign in"
                )
            }
        }
    }

    fun registerWithEmail(email: String, pass: String, name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthLoading = true, authErrorMessage = null)
            val result = authRepo.registerWithEmail(email, pass, name)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isAuthLoading = false,
                    authErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to register"
                )
            }
        }
    }

    fun signOut() {
        authRepo.signOut()
        resetGarageState()
    }

    fun createGarage(name: String) {
        val user = _uiState.value.currentUser
        if (user == null) {
            _uiState.value = _uiState.value.copy(
                garageErrorMessage = "Please sign in with Google before creating a garage."
            )
            return
        }

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
        val user = _uiState.value.currentUser
        if (user == null) {
            _uiState.value = _uiState.value.copy(
                garageErrorMessage = "Please sign in with Google to join a shared garage."
            )
            return
        }

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
                    garageErrorMessage = "This garage already has 2 members (limit reached)."
                )
                return@launch
            }

            val requestResult = garageRepo.requestToJoinGarage(targetGarage.id, code)
            _uiState.value = _uiState.value.copy(isGarageLoading = false)
            if (requestResult.isSuccess) {
                _uiState.value = _uiState.value.copy(pendingRequestGarage = targetGarage)
                listenForApproval(targetGarage.id, user.uid)
            } else {
                _uiState.value = _uiState.value.copy(
                    garageErrorMessage = requestResult.exceptionOrNull()?.localizedMessage ?: "Failed to submit request"
                )
            }
        }
    }

    private fun listenForApproval(garageId: String, uid: String) {
        myJoinRequestObserveJob?.cancel()
        myJoinRequestObserveJob = viewModelScope.launch {
            garageRepo.observeMyJoinRequest(garageId, uid).collect { req ->
                if (req != null && req.status == "approved") {
                    authRepo.updateUserCurrentGarage(garageId)
                    _uiState.value = _uiState.value.copy(pendingRequestGarage = null)
                    observeActiveGarage(garageId)
                    myJoinRequestObserveJob?.cancel()
                }
            }
        }
    }

    fun checkPendingApproval() {
        val user = _uiState.value.currentUser ?: return
        val pendingGarage = _uiState.value.pendingRequestGarage ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGarageLoading = true)
            val status = garageRepo.checkJoinRequestStatus(pendingGarage.id, user.uid)
            if (status == "approved") {
                authRepo.updateUserCurrentGarage(pendingGarage.id)
                _uiState.value = _uiState.value.copy(pendingRequestGarage = null, isGarageLoading = false)
                observeActiveGarage(pendingGarage.id)
            } else {
                _uiState.value = _uiState.value.copy(isGarageLoading = false)
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
        if (_uiState.value.currentUser == null) return

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
        val garageId = _uiState.value.currentGarage?.id
        if (garageId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(autoSaveStatus = AutoSaveStatus.FAILED)
            return Result.failure(IllegalStateException("No active garage. Please create or join a garage."))
        }
        if (_uiState.value.currentUser == null) {
            _uiState.value = _uiState.value.copy(autoSaveStatus = AutoSaveStatus.FAILED)
            return Result.failure(IllegalStateException("Please sign in with Google to save records to Cloud Firestore."))
        }

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
