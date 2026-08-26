package com.example.jalraksha.data.auth

import android.app.Activity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory auth used until `google-services.json` lands in `app/`.
 *
 * It accepts any 10-digit number with a password of six characters or more, so the four screens
 * can be walked end to end on a device with no backend at all. The artificial delay keeps the
 * button's loading state honest.
 */
class FakeAuthRepository : AuthRepository {

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser

    override suspend fun signInWithPassword(phone: String, password: String): Result<AuthUser> {
        delay(NETWORK_DELAY_MS)
        if (phone.length != 10) {
            return Result.failure(AuthException(AuthFailure.PHONE_LENGTH))
        }
        if (password.length < 6) {
            return Result.failure(AuthException(AuthFailure.PASSWORD_SHORT))
        }
        val user = AuthUser(uid = "local-$phone", phone = "+91$phone")
        _currentUser.value = user
        return Result.success(user)
    }

    override suspend fun register(phone: String, password: String) = signInWithPassword(phone, password)

    override suspend fun requestOtp(phone: String, activity: Activity): Result<OtpRequest> {
        delay(NETWORK_DELAY_MS)
        return Result.success(OtpRequest.CodeSent(verificationId = "local-$phone"))
    }

    override suspend fun verifyOtp(verificationId: String, code: String): Result<AuthUser> {
        delay(NETWORK_DELAY_MS)
        val phone = verificationId.removePrefix("local-")
        val user = AuthUser(uid = "local-$phone", phone = "+91$phone")
        _currentUser.value = user
        return Result.success(user)
    }

    override suspend fun sendPasswordReset(phone: String): Result<Unit> {
        delay(NETWORK_DELAY_MS)
        return Result.success(Unit)
    }

    override suspend fun signOut() {
        _currentUser.value = null
    }

    private companion object {
        const val NETWORK_DELAY_MS = 700L
    }
}
