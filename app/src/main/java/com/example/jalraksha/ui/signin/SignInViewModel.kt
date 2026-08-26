package com.example.jalraksha.ui.signin

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.jalraksha.R
import com.example.jalraksha.ServiceLocator
import com.example.jalraksha.data.SessionStore
import com.example.jalraksha.data.auth.AuthException
import com.example.jalraksha.data.auth.AuthFailure
import com.example.jalraksha.data.auth.AuthRepository
import com.example.jalraksha.data.auth.OtpRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A message to show the user, held as a resource id rather than a string.
 *
 * The ViewModel outlives any single composition and has no locale of its own; resolving text here
 * would freeze it in whatever language was current when the error happened, and it would stop
 * matching the screen the moment the user changed language.
 */
data class UiMessage(@param:StringRes val id: Int, val isError: Boolean)

data class SignInUiState(
    val phone: String = "",
    val password: String = "",
    val keepSignedIn: Boolean = true,
    val loading: Boolean = false,
    val message: UiMessage? = null,
    /** Non-null once an OTP has been sent — the code dialog is showing. */
    val otpVerificationId: String? = null,
    val otpCode: String = "",
    val signedIn: Boolean = false,
) {
    /** The primary button only lights up once both fields could plausibly succeed. */
    val canSubmit: Boolean get() = phone.length == 10 && password.length >= 6
    val canRequestOtp: Boolean get() = phone.length == 10
}

class SignInViewModel(
    private val auth: AuthRepository,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value, message = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, message = null) }

    fun onKeepSignedInChange(value: Boolean) {
        _state.update { it.copy(keepSignedIn = value) }
        viewModelScope.launch { session.setKeepSignedIn(value) }
    }

    fun onOtpCodeChange(value: String) =
        _state.update { it.copy(otpCode = value.filter(Char::isDigit).take(6), message = null) }

    fun signIn() = runAuth { auth.signInWithPassword(state.value.phone, state.value.password) }

    fun register() = runAuth { auth.register(state.value.phone, state.value.password) }

    fun requestOtp(activity: Activity) {
        val phone = state.value.phone
        if (phone.length != 10) return
        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            auth.requestOtp(phone, activity)
                .onSuccess { request ->
                    when (request) {
                        is OtpRequest.CodeSent -> _state.update {
                            it.copy(
                                loading = false,
                                otpVerificationId = request.verificationId,
                                message = UiMessage(R.string.otp_code_sent, isError = false),
                            )
                        }
                        // The SIM verified itself; skip the code dialog entirely.
                        is OtpRequest.AutoVerified -> _state.update {
                            it.copy(loading = false, signedIn = true)
                        }
                    }
                }
                .onFailure { error -> _state.update { it.copy(loading = false, message = error.asMessage()) } }
        }
    }

    fun verifyOtp() {
        val verificationId = state.value.otpVerificationId ?: return
        runAuth { auth.verifyOtp(verificationId, state.value.otpCode) }
    }

    fun dismissOtp() = _state.update { it.copy(otpVerificationId = null, otpCode = "", message = null) }

    fun forgotPassword() {
        val phone = state.value.phone
        if (phone.length != 10) {
            _state.update {
                it.copy(message = UiMessage(R.string.error_enter_phone_first, isError = true))
            }
            return
        }
        viewModelScope.launch {
            auth.sendPasswordReset(phone)
                .onSuccess {
                    _state.update {
                        it.copy(message = UiMessage(R.string.info_password_reset_sent, isError = false))
                    }
                }
                .onFailure { error -> _state.update { it.copy(message = error.asMessage()) } }
        }
    }

    private fun runAuth(block: suspend () -> Result<*>) {
        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            block()
                .onSuccess { _state.update { it.copy(loading = false, signedIn = true, otpVerificationId = null) } }
                .onFailure { error -> _state.update { it.copy(loading = false, message = error.asMessage()) } }
        }
    }

    companion object {
        /**
         * The repository has already worked out *what* went wrong; this only decides how to say
         * it. Villagers never see a Firebase error code.
         */
        private fun Throwable.asMessage(): UiMessage {
            val id = when ((this as? AuthException)?.failure ?: AuthFailure.UNKNOWN) {
                AuthFailure.PHONE_LENGTH -> R.string.error_phone_length
                AuthFailure.PASSWORD_SHORT -> R.string.error_password_short
                AuthFailure.BAD_CREDENTIALS -> R.string.error_bad_credentials
                AuthFailure.NO_ACCOUNT -> R.string.error_no_account
                AuthFailure.NETWORK -> R.string.error_network
                AuthFailure.TOO_MANY_ATTEMPTS -> R.string.error_too_many_attempts
                AuthFailure.UNKNOWN -> R.string.error_generic
            }
            return UiMessage(id, isError = true)
        }

        val Factory = viewModelFactory {
            initializer {
                SignInViewModel(ServiceLocator.authRepository, ServiceLocator.sessionStore)
            }
        }
    }
}
