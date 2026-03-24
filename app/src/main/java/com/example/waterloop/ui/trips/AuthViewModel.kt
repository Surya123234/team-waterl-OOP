package com.example.waterloop.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterloop.data.repository.AuthRepository
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// manages auth state for the ui. the ui observes the flows below and reacts to changes.
// never calls the repository directly -- all auth logic goes through here.
class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    // tracks whether the user is logged in.
    // null = still checking on startup, true = logged in, false = not logged in
    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn

    // true while a network call is in flight (sign in or sign up)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // holds an error message to show the user. cleared after the ui displays it.
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // runs once when the viewmodel is first created
    init {
        checkAuthState()
    }

    // checks supabase for an existing session so returning users don't have to log in again
    private fun checkAuthState() {
        viewModelScope.launch {
            // DEBUGGING: Log.d("AuthViewModel", "checking auth state on startup")
            delay(1000) // artificial delay to show the loading screen — remove before release
            _isLoggedIn.value = repository.isLoggedIn()
            // DEBUGGING: Log.d("AuthViewModel", "isLoggedIn = ${_isLoggedIn.value}")
        }
    }

    // returns the email of the currently logged in user, or null if no one is logged in
    fun getCurrentUserEmail(): String? {
        return repository.getCurrentUserEmail()
    }

    // validates inputs before making a network call. returns an error string or null if valid.
    private fun validate(email: String, password: String): String? {
        if (email.isBlank()) return "Email is required"
        if (!email.contains("@")) return "Please enter a valid email address"
        if (password.isBlank()) return "Password is required"
        if (password.length < 6) return "Password must be at least 6 characters"
        return null
    }

    // maps supabase error codes to readable messages.
    // matching on errorCode (not message string) as supabase says message strings are not stable.
    private fun parseError(e: Exception): String {
        return if (e is RestException) {
            when (e.error) {
                "invalid_credentials" -> "Incorrect email or password"
                "user_already_exists", "email_exists" -> "An account with this email already exists"
                "weak_password" -> "Password must be at least 6 characters"
                "email_address_invalid" -> "Please enter a valid email address"
                "email_not_confirmed" -> "Please confirm your email before signing in"
                "over_request_rate_limit", "over_email_send_rate_limit" -> "Too many attempts, please slow down"
                else -> "Something went wrong. Please try again"
            }
        } else {
            "Something went wrong. Please try again"
        }
    }

    // handles the shared setup and teardown for sign in and sign up.
    // runs validation first, then the actual auth call. the errorLabel is unused after
    // parseError, but kept for debugging clarity.
    private fun performAuthAction(email: String, password: String, action: suspend () -> Unit) {
        val validationError = validate(email, password)
        if (validationError != null) {
            _errorMessage.value = validationError
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                action()
                _isLoggedIn.value = true
            } catch (e: Exception) {
                _errorMessage.value = parseError(e)
                // DEBUGGING: Log.d("AuthViewModel", "auth failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // signs in an existing user. on success, isLoggedIn flips to true and the ui navigates away.
    fun signIn(email: String, password: String) {
        // DEBUGGING: Log.d("AuthViewModel", "signIn attempt for $email")
        performAuthAction(email, password) { repository.signIn(email, password) }
    }

    // creates a new user account. on success, isLoggedIn flips to true and the ui navigates away.
    fun signUp(email: String, password: String) {
        // DEBUGGING: Log.d("AuthViewModel", "signUp attempt for $email")
        performAuthAction(email, password) { repository.signUp(email, password) }
    }

    // signs out the current user. isLoggedIn flips to false and the ui returns to the login screen.
    fun signOut() {
        viewModelScope.launch {
            // DEBUGGING: Log.d("AuthViewModel", "signOut attempt")
            try {
                repository.signOut()
                _isLoggedIn.value = false
                // DEBUGGING: Log.d("AuthViewModel", "signOut success")
            } catch (e: Exception) {
                _errorMessage.value = parseError(e)
                // DEBUGGING: Log.d("AuthViewModel", "signOut failed: ${e.message}")
            }
        }
    }

    // called by the ui after it has displayed the error, so it doesn't show again
    fun clearError() {
        _errorMessage.value = null
    }
}
