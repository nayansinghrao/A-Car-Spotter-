package com.example.data.repository

import android.content.Context
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirestoreProvider.get(context)
    private val credentialManager = CredentialManager.create(context)

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithGoogle(): Result<FirebaseUser> {
        return try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(authCredential).await()
            val user = authResult.user ?: throw IllegalStateException("Firebase user is null after sign in")

            // Sync user profile document in Firestore
            syncUserProfile(user)

            Result.success(user)
        } catch (e: GetCredentialCancellationException) {
            Log.w("AuthRepository", "Sign-in cancelled by user")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign-in error: ${e.message}", e)
            Result.failure(e)
        }
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
