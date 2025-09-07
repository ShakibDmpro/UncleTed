package com.hamoon.uncleted

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.ActivityLockScreenBinding
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.services.PanicActionService.Severity

class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private val TAG = "LockScreenActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reason = intent.getStringExtra("REASON")
        if (reason == "UNINSTALL_ATTEMPT") {
            binding.tvTitleLockScreen.text = getString(R.string.unauthorized_action_title)
            binding.tvSubtitleLockScreen.text = getString(R.string.unauthorized_action_subtitle)
        }

        val normalPin = SecurityPreferences.getNormalPin(this)
        val duressPin = SecurityPreferences.getDuressPin(this)
        val wipePin = SecurityPreferences.getWipePin(this)

        // ### CRITICAL FIX: Secure failure if PINs are not configured ###
        // If the PINs aren't set, the lock screen MUST NOT be dismissible.
        // It should block the user completely, as this indicates a misconfigured
        // but active security state.
        if (normalPin.isNullOrEmpty() || duressPin.isNullOrEmpty() || wipePin.isNullOrEmpty()) {
            binding.tvTitleLockScreen.text = "Configuration Error"
            binding.tvSubtitleLockScreen.text = "Security PINs have not been set. Unlock is not possible."
            binding.etPinEntry.isEnabled = false // Disable input
            binding.btnUnlock.isEnabled = false // Disable button
            // DO NOT call finish(). The lock screen must remain.
        }

        binding.btnUnlock.setOnClickListener {
            val enteredPin = binding.etPinEntry.text.toString()

            // This check is now redundant due to the check above, but we keep it
            // as a defensive measure in case the logic is ever changed.
            if (normalPin.isNullOrEmpty() || duressPin.isNullOrEmpty() || wipePin.isNullOrEmpty()) {
                Toast.makeText(this, "PINs not configured!", Toast.LENGTH_SHORT).show()
                // The dangerous finish() call is removed here as well.
                return@setOnClickListener
            }

            when (enteredPin) {
                normalPin -> {
                    Log.d(TAG, "Normal PIN entered correctly. Unlocking.")
                    SecurityPreferences.resetFailedAttempts(this)
                    finish()
                }
                duressPin -> {
                    Log.w(TAG, "Duress PIN entered! Triggering panic service.")
                    PanicActionService.trigger(this, "DURESS_PIN", PanicActionService.Severity.HIGH)
                    finish()
                }
                wipePin -> {
                    Log.e(TAG, "Wipe PIN entered! Triggering CRITICAL wipe service.")
                    PanicActionService.trigger(this, "WIPE_PIN", PanicActionService.Severity.CRITICAL)
                    finish()
                }
                else -> {
                    handleFailedAttempt()
                    binding.etPinEntry.text?.clear()
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing. The only way out is a correct PIN.
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun handleFailedAttempt() {
        SecurityPreferences.incrementFailedAttempts(this)
        val attempts = SecurityPreferences.getFailedAttempts(this)
        val maxAttempts = 3
        Log.d(TAG, "Failed unlock attempt. Current count: $attempts")

        if (SecurityPreferences.isIntruderSelfieEnabled(this) && attempts >= maxAttempts) {
            Log.w(TAG, "Max failed attempts threshold reached. Triggering intruder selfie.")
            PanicActionService.trigger(this, "INTRUDER_SELFIE", PanicActionService.Severity.MEDIUM)
        }
    }
}