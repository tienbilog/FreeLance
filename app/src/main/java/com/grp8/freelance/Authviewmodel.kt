package com.grp8.freelance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean   = false,
    val errorMessage: String? = null
)

class AuthViewModel : ViewModel() {

    private val authRepo = AuthRepository()

    val currentUser: StateFlow<FirebaseUser?> =
        authRepo.currentUserFlow.let { flow ->
            val state = MutableStateFlow(authRepo.currentUser)
            viewModelScope.launch { flow.collect { state.value = it } }
            state.asStateFlow()
        }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signUp(username: String, password: String, onSuccess: () -> Unit) {
        if (!validate(username, password)) return
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            val error = authRepo.signUp(username, password)
            if (error == null) {
                _uiState.value = AuthUiState()
                onSuccess()
            } else {
                _uiState.value = AuthUiState(errorMessage = error)
            }
        }
    }

    fun signIn(username: String, password: String, onSuccess: () -> Unit) {
        if (!validate(username, password)) return
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            val error = authRepo.signIn(username, password)
            if (error == null) {
                _uiState.value = AuthUiState()
                onSuccess()
            } else {
                _uiState.value = AuthUiState(errorMessage = error)
            }
        }
    }

    fun signOut() = authRepo.signOut()

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    private fun validate(username: String, password: String): Boolean {
        return when {
            username.isBlank() -> {
                _uiState.value = AuthUiState(errorMessage = "Username cannot be empty.")
                false
            }
            username.length < 3 -> {
                _uiState.value = AuthUiState(errorMessage = "Username must be at least 3 characters.")
                false
            }
            !username.matches(Regex("[a-zA-Z0-9_]+")) -> {
                _uiState.value = AuthUiState(errorMessage = "Only letters, numbers, and underscores allowed.")
                false
            }
            password.length < 6 -> {
                _uiState.value = AuthUiState(errorMessage = "Password must be at least 6 characters.")
                false
            }
            else -> true
        }
    }
}

