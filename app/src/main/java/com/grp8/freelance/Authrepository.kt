package com.grp8.freelance

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.grp8.freelance.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import android.util.Log

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
    private val DEBUG_AUTH = true // Set to true to see raw Firebase errors in UI

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
            Log.e("AuthRepository", "Sign-up failed", e)
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
            Log.e("AuthRepository", "Sign-in failed", e)
            friendlyError(e)
        }
    }

    fun signOut() = auth.signOut()

    // Converts a username to a stable internal email Firebase Auth will accept.
    private fun toEmail(username: String) =
        "${username.trim().lowercase()}@freelanceapp.com"

    private fun friendlyError(e: Exception): String {
        val userMessage = when (e) {
            is FirebaseAuthUserCollisionException -> "That username is already taken."
            is FirebaseAuthInvalidUserException -> "Username not found."
            is FirebaseAuthInvalidCredentialsException -> "Incorrect password or invalid username format."
            is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters."
            is FirebaseNetworkException -> "No internet connection."
            else -> "Something went wrong. Try again."
        }

        return if (DEBUG_AUTH) {
            val debugInfo = if (e is FirebaseAuthException) {
                "[DEBUG] Error Code: ${e.errorCode}\nMessage: ${e.message}"
            } else {
                "[DEBUG] ${e.message}"
            }
            "$debugInfo\n\n$userMessage"
        } else {
            userMessage
        }
    }
}