package com.grp8.freelance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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

                when {
                    isAuthenticated || guestMode -> {
                        SchedulerApp(
                            viewModel = schedViewModel,
                            username  = currentUser?.displayName,
                            onSignOut = {
                                authViewModel.signOut()
                                guestMode = false
                            }
                        )
                    }
                    else -> {
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