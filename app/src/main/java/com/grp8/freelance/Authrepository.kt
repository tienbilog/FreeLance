package com.grp8.freelance

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.grp8.freelance.ui.theme.*

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onContinueAsGuest: () -> Unit
) package com.grp8.freelance

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Wraps Firebase Auth with username-only sign-in.
 *
 * Firebase Auth has no native "username" provider, so we map:
 *   username  →  "$username@freelance.internal"  (hidden from the user)
 *   password  →  user-chosen password
 *
 * The display name stored on the FirebaseUser is the plain username.
 */
class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Emits the current user whenever auth state changes (null = signed out / guest). */
    val currentUserFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** True when a real (non-anonymous) account is active. */
    val isSignedIn: Boolean get() = currentUser != null && !currentUser!!.isAnonymous

    // -------------------------------------------------------------------------
    // Sign-up: create account with username + password.
    // Returns null on success, or an error message string on failure.
    // -------------------------------------------------------------------------
    suspend fun signUp(username: String, password: String): String? {
        return try {
            val email = toEmail(username)
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            // Store the plain username as the display name so we can show it in the UI.
            result.user?.updateProfile(
                userProfileChangeRequest { displayName = username }
            )?.await()
            null  // success
        } catch (e: Exception) {
            friendlyError(e)
        }
    }

    // -------------------------------------------------------------------------
    // Sign-in: existing account.
    // Returns null on success, or an error message string on failure.
    // -------------------------------------------------------------------------
    suspend fun signIn(username: String, password: String): String? {
        return try {
            val email = toEmail(username)
            auth.signInWithEmailAndPassword(email, password).await()
            null  // success
        } catch (e: Exception) {
            friendlyError(e)
        }
    }

    fun signOut() = auth.signOut()

    // Converts a username to a stable internal email Firebase Auth will accept.
    private fun toEmail(username: String) =
        "${username.trim().lowercase()}@freelance.internal"

    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            "email address is already in use" in msg -> "That username is already taken."
            "no user record"                  in msg -> "Username not found."
            "password is invalid"             in msg -> "Incorrect password."
            "badly formatted"                 in msg -> "Invalid username."
            "least 6 characters"              in msg -> "Password must be at least 6 characters."
            "network error"                   in msg -> "No internet connection."
            else                                     -> "Something went wrong. Try again."
        }
    }
}