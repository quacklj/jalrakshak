package com.example.jalraksha.data.auth

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/** The signed-in user, as far as the UI is concerned. */
data class AuthUser(
    val uid: String,
    /** E.164, e.g. `+919876543210`. */
    val phone: String,
)

/**
 * Why a sign-in attempt failed, classified where the failure is understood.
 *
 * The data layer knows what Firebase's error codes mean; the UI layer knows how to say it in eight
 * languages. This enum is the seam — nothing here is display text.
 */
enum class AuthFailure {
    PHONE_LENGTH,
    PASSWORD_SHORT,
    BAD_CREDENTIALS,
    NO_ACCOUNT,
    NETWORK,
    TOO_MANY_ATTEMPTS,
    UNKNOWN,
}

/** A classified authentication failure. [cause] keeps the original for logging. */
class AuthException(
    val failure: AuthFailure,
    override val cause: Throwable? = null,
) : Exception(failure.name, cause)

/** Result of an OTP request: either a code is on its way, or the number was auto-verified. */
sealed interface OtpRequest {
    /** [verificationId] is what [AuthRepository.verifyOtp] needs alongside the typed code. */
    data class CodeSent(val verificationId: String) : OtpRequest

    /** Play Services recognised the SIM and signed the user in without a code. */
    data class AutoVerified(val user: AuthUser) : OtpRequest
}

/**
 * Authentication, kept behind an interface so the whole app runs against [FakeAuthRepository]
 * until `google-services.json` is dropped in.
 *
 * Phone numbers are passed in as 10 raw digits; implementations add the `+91`.
 */
interface AuthRepository {

    /** Emits the current user, or null when signed out. Emits immediately on collection. */
    val currentUser: Flow<AuthUser?>

    suspend fun signInWithPassword(phone: String, password: String): Result<AuthUser>

    suspend fun register(phone: String, password: String): Result<AuthUser>

    /**
     * Starts phone verification. Needs an [Activity] because Play Integrity may show a
     * reCAPTCHA fallback on devices without Play Services attestation.
     */
    suspend fun requestOtp(phone: String, activity: Activity): Result<OtpRequest>

    suspend fun verifyOtp(verificationId: String, code: String): Result<AuthUser>

    suspend fun sendPasswordReset(phone: String): Result<Unit>

    suspend fun signOut()
}
