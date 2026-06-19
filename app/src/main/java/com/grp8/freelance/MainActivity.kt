package com.grp8.freelance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import com.grp8.freelance.ui.components.AppOnboardingDialog
import com.grp8.freelance.ui.theme.FreelanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreelanceTheme {
                val authViewModel: AuthViewModel     = viewModel()
                val schedViewModel: SchedulerViewModel = viewModel()

                val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()

                // Whenever auth state changes, tell SchedulerViewModel which
                // data source to use (local DataStore vs Firestore).
                LaunchedEffect(currentUser) {
                    schedViewModel.onUserChanged(currentUser)
                }

                // ------------------------------------------------------------------
                // Navigation logic:
                //
                // • No user at all → show AuthScreen (sign-in / guest choice)
                // • Guest mode     → show SchedulerApp (local data)
                // • Signed in      → show SchedulerApp (cloud data)
                //
                // "Guest mode" is represented by a local boolean rather than a
                // Firebase anonymous account, so there's no unnecessary network call.
                // ------------------------------------------------------------------
                var guestMode by remember { mutableStateOf(false) }
                val isAuthenticated = currentUser != null && !currentUser!!.isAnonymous
                val hasCompletedOnboarding by schedViewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()

                when {
                    isAuthenticated || guestMode -> {
                        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)) {
                            SchedulerApp(
                                viewModel = schedViewModel,
                                username  = currentUser?.displayName,
                                onSignOut = {
                                    authViewModel.signOut()
                                    guestMode = false
                                }
                            )

                            if (!hasCompletedOnboarding) {
                                AppOnboardingDialog(
                                    onDismiss = { schedViewModel.completeOnboarding() },
                                    onGetStarted = { schedViewModel.completeOnboarding() }
                                )
                            }
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)) {
                            AuthScreen(
                                authViewModel    = authViewModel,
                                onContinueAsGuest = { guestMode = true }
                            )
                        }
                    }
                }
            }
        }
    }
}