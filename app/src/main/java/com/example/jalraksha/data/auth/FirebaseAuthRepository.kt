package com.example.jalraksha.data.auth

import android.app.Activity
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Firebase-backed authentication.
 *
 * Firebase has no phone+password provider, so the password path stores the account under a
 * synthetic email derived from the number (`919876543210@phone.jalraksha.app`). The number itself
 * still lives on the user's Firestore profile, and the OTP path uses real phone auth — so an
 * account created either way resolves to the same person.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthRepository {

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithPassword(phone: String, password: String): Result<AuthUser> =
        classified {
            auth.signInWithEmailAndPassword(syntheticEmail(phone), password).await()
                .user!!.toAuthUser(phone)
        }

    override suspend fun register(phone: String, password: String): Result<AuthUser> =
        classified {
            auth.createUserWithEmailAndPassword(syntheticEmail(phone), password).await()
                .user!!.toAuthUser(phone)
        }

    override suspend fun requestOtp(phone: String, activity: Activity): Result<OtpRequest> =
        classified {
            suspendCancellableCoroutine { continuation ->
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        // The SIM was recognised — finish the sign-in rather than asking for a code.
                        auth.signInWithCredential(credential)
                            .addOnSuccessListener { result ->
                                if (continuation.isActive) {
                                    continuation.resume(
                                        OtpRequest.AutoVerified(result.user!!.toAuthUser(phone)),
                                    )
                                }
                            }
                            .addOnFailureListener { error ->
                                if (continuation.isActive) continuation.cancel(error)
                            }
                    }

                    override fun onVerificationFailed(error: com.google.firebase.FirebaseException) {
                        if (continuation.isActive) continuation.cancel(error)
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken,
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(OtpRequest.CodeSent(verificationId))
                        }
                    }
                }

                PhoneAuthProvider.verifyPhoneNumber(
                    PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(e164(phone))
                        .setTimeout(OTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .setActivity(activity)
                        .setCallbacks(callbacks)
                        .build(),
                )
            }
        }

    override suspend fun verifyOtp(verificationId: String, code: String): Result<AuthUser> =
        classified {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            auth.signInWithCredential(credential).await().user!!.toAuthUser()
        }

    override suspend fun sendPasswordReset(phone: String): Result<Unit> = classified {
        auth.sendPasswordResetEmail(syntheticEmail(phone)).await()
    }

    override suspend fun signOut() = auth.signOut()

    /**
     * Runs [block] and turns whatever Firebase throws into an [AuthFailure] the UI can phrase.
     *
     * Firebase reports failures as error-code strings meant for developers; classifying them here
     * keeps that vocabulary out of the screens, which only know how to say things in eight
     * languages.
     */
    private suspend fun <T> classified(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(AuthException(error.toFailure(), error))
    }

    private fun Throwable.toFailure(): AuthFailure = when {
        this is AuthException -> failure
        this is FirebaseAuthInvalidUserException -> AuthFailure.NO_ACCOUNT
        this is FirebaseAuthInvalidCredentialsException -> AuthFailure.BAD_CREDENTIALS
        this is FirebaseTooManyRequestsException -> AuthFailure.TOO_MANY_ATTEMPTS
        this is FirebaseNetworkException || this is IOException -> AuthFailure.NETWORK
        else -> AuthFailure.UNKNOWN
    }

    private fun FirebaseUser.toAuthUser(fallbackPhone: String? = null) = AuthUser(
        uid = uid,
        phone = phoneNumber ?: fallbackPhone?.let(::e164) ?: "",
    )

    private companion object {
        const val OTP_TIMEOUT_SECONDS = 60L

        fun e164(phone: String) = "+91${phone.filter(Char::isDigit).takeLast(10)}"

        /** Firebase's email/password provider needs an address; the number is the identity. */
        fun syntheticEmail(phone: String) = "91${phone.filter(Char::isDigit).takeLast(10)}@phone.jalraksha.app"
    }
}
