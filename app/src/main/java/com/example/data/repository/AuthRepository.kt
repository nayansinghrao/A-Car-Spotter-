package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.model.UserProfile
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.example.util.DiagnosticLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirestoreProvider.get(context)

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            DiagnosticLogger.logInfo(
                tag = "AUTH_STATE",
                message = if (user != null) "User active: ${user.email ?: user.uid}" else "User session is empty"
            )
            trySend(user)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithGoogle(activityContext: Context = context): Result<FirebaseUser> {
        val resolvedActivity = findActivity(activityContext) ?: (activityContext as? Activity)
        val launchContext = resolvedActivity ?: activityContext

        DiagnosticLogger.logInfo(
            tag = "AUTH",
            message = "Starting Google Sign-In with CredentialManager",
            details = "launchContext: ${launchContext::class.java.simpleName}, isActivity: ${launchContext is Activity}"
        )

        return try {
            val credentialManager = CredentialManager.create(launchContext)

            val serverClientId = context.getString(R.string.default_web_client_id)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            DiagnosticLogger.logInfo("AUTH", "Launching CredentialManager selector UI...")
            val result = credentialManager.getCredential(
                request = request,
                context = launchContext
            )

            DiagnosticLogger.logSuccess("AUTH", "Received Google ID token from selector UI")
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(authCredential).await()
            val user = authResult.user ?: throw IllegalStateException("Firebase user is null after sign in")

            // Sync user profile document in Firestore
            syncUserProfile(user)

            DiagnosticLogger.logSuccess(
                tag = "AUTH",
                message = "Firebase authentication succeeded for: ${user.email ?: user.uid}"
            )
            DiagnosticLogger.showToast(launchContext, "Signed in successfully as ${user.email ?: "Spotter"}", isLong = false)

            Result.success(user)
        } catch (e: GetCredentialCancellationException) {
            val code = "CANCELED (16)"
            Log.w("AuthRepository", "Sign-in cancelled by user")
            DiagnosticLogger.logWarn(
                tag = "AUTH",
                message = "Google Sign-In selector was dismissed or cancelled by user",
                errorCode = code
            )
            DiagnosticLogger.showToast(launchContext, "Sign-in was cancelled", isLong = false)
            Result.failure(e)
        } catch (e: Exception) {
            val code = DiagnosticLogger.parseErrorCode(e)
            Log.e("AuthRepository", "Sign-in error [$code]: ${e.message}", e)
            DiagnosticLogger.logError(
                tag = "AUTH",
                message = "Sign-in failed with error code: $code",
                errorCode = code,
                throwable = e
            )
            DiagnosticLogger.showToast(launchContext, "Sign-in Error [$code]: ${e.localizedMessage ?: "Unknown error"}", isLong = true)
            Result.failure(e)
        }
    }

    private fun findActivity(ctx: Context): Activity? {
        var current = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private suspend fun syncUserProfile(user: FirebaseUser) {
        try {
            val userDocRef = firestore.collection("users").document(user.uid)
            val snapshot = userDocRef.get().await()

            if (!snapshot.exists()) {
                val data = hashMapOf(
                    "uid" to user.uid,
                    "email" to (user.email ?: ""),
                    "displayName" to (user.displayName ?: "Spotter"),
                    "currentGarageId" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDocRef.set(data).await()
            } else {
                val updateData = hashMapOf(
                    "email" to (user.email ?: ""),
                    "displayName" to (user.displayName ?: snapshot.getString("displayName") ?: "Spotter"),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDocRef.set(updateData, SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to sync user profile: ${e.message}")
        }
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) {
                UserProfile(
                    uid = doc.getString("uid") ?: uid,
                    email = doc.getString("email") ?: "",
                    displayName = doc.getString("displayName") ?: "",
                    currentGarageId = doc.getString("currentGarageId") ?: "",
                    createdAt = doc.getTimestamp("createdAt"),
                    updatedAt = doc.getTimestamp("updatedAt")
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUserCurrentGarage(garageId: String) {
        val user = currentUser ?: return
        try {
            firestore.collection("users").document(user.uid)
                .update(
                    mapOf(
                        "currentGarageId" to garageId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to update currentGarageId: ${e.message}")
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
