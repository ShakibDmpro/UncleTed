package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import java.util.concurrent.Executor

object BiometricAuthManager {

    private const val TAG = "BiometricAuthManager"
    private const val MAX_FAILED_BIOMETRIC_ATTEMPTS = 5

    enum class AuthResult {
        SUCCESS, FAILED, ERROR, UNAVAILABLE, HARDWARE_UNAVAILABLE
    }

    interface AuthCallback {
        fun onAuthResult(result: AuthResult, errorMessage: String? = null)
    }

    fun isBiometricAvailable(context: Context): Boolean {
        return when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    fun authenticateUser(
        activity: FragmentActivity,
        title: String = "Biometric Authentication",
        subtitle: String = "Use your fingerprint or face to authenticate",
        callback: AuthCallback
    ) {
        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e(TAG, "Biometric authentication error: $errString")

                // Check if this is a security threat (too many failed attempts)
                if (errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                    handleBiometricLockout(activity)
                }

                callback.onAuthResult(AuthResult.ERROR, errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.i(TAG, "Biometric authentication succeeded")
                SecurityPreferences.resetFailedBiometricAttempts(activity)
                callback.onAuthResult(AuthResult.SUCCESS)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "Biometric authentication failed")
                handleFailedBiometricAttempt(activity)
                callback.onAuthResult(AuthResult.FAILED)
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            // ### THE FIX IS HERE: The following line has been removed ###
            // .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun handleFailedBiometricAttempt(context: Context) {
        SecurityPreferences.incrementFailedBiometricAttempts(context)
        val attempts = SecurityPreferences.getFailedBiometricAttempts(context)

        Log.w(TAG, "Failed biometric attempt #$attempts")
        EventLogger.log(context, "Failed biometric authentication attempt #$attempts")

        if (attempts >= MAX_FAILED_BIOMETRIC_ATTEMPTS) {
            Log.e(TAG, "Maximum biometric attempts exceeded. Triggering security response.")
            PanicActionService.trigger(context, "BIOMETRIC_INTRUSION", PanicActionService.Severity.HIGH)
        }
    }

    private fun handleBiometricLockout(context: Context) {
        Log.e(TAG, "Biometric authentication locked out - potential attack detected")
        EventLogger.log(context, "CRITICAL: Biometric lockout detected - possible attack")
        PanicActionService.trigger(context, "BIOMETRIC_LOCKOUT", PanicActionService.Severity.CRITICAL)
    }
}

// Extension functions for SecurityPreferences
private fun SecurityPreferences.getFailedBiometricAttempts(context: Context): Int =
    getInstance(context).getInt("FAILED_BIOMETRIC_ATTEMPTS", 0)

private fun SecurityPreferences.incrementFailedBiometricAttempts(context: Context) {
    val current = getFailedBiometricAttempts(context)
    getInstance(context).edit().putInt("FAILED_BIOMETRIC_ATTEMPTS", current + 1).apply()
}

private fun SecurityPreferences.resetFailedBiometricAttempts(context: Context) =
    getInstance(context).edit().putInt("FAILED_BIOMETRIC_ATTEMPTS", 0).apply()