package com.hamoon.uncleted.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hamoon.uncleted.R
import com.hamoon.uncleted.MainActivity

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private const val SELFIE_SAVED_CHANNEL_ID = "UncleTedSelfieSavedChannel"
    private const val PANIC_SERVICE_CHANNEL_ID = "PanicServiceChannel"
    private const val BASIC_SERVICE_CHANNEL_ID = "BasicServiceChannel"
    private const val SELFIE_SAVED_NOTIFICATION_ID = 101

    fun createPanicNotification(context: Context): Notification {
        val channelName = "Uncle Ted Emergency Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PANIC_SERVICE_CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Runs essential security monitoring and emergency actions in the background."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, PANIC_SERVICE_CHANNEL_ID)
            .setContentTitle("Uncle Ted")
            .setContentText("Performing security action...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun createBasicNotification(context: Context): Notification {
        val channelName = "Uncle Ted Basic Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BASIC_SERVICE_CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Basic security service notification."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, BASIC_SERVICE_CHANNEL_ID)
            .setContentTitle("Uncle Ted")
            .setContentText("Security service running...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun showSelfieSavedNotification(context: Context, imageUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Cannot show selfie saved notification due to missing POST_NOTIFICATIONS permission.")
                return
            }
        }

        val notificationManager = NotificationManagerCompat.from(context)
        createSelfieSavedNotificationChannel(context, notificationManager)

        val viewIntent = Intent(Intent.ACTION_VIEW, imageUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBody = "Tap to view the saved image."
        val expandedStyle = NotificationCompat.BigTextStyle()
            .bigText("Saved to: Pictures/UncleTed/\n${imageUri.lastPathSegment}")

        val builder = NotificationCompat.Builder(context, SELFIE_SAVED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_selfie_24)
            .setContentTitle(context.getString(R.string.selfie_saved_notification_title))
            .setContentText(notificationBody)
            .setStyle(expandedStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            notificationManager.notify(SELFIE_SAVED_NOTIFICATION_ID, builder.build())
            Log.d(TAG, "Displayed selfie saved notification.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to show selfie saved notification", e)
        }
    }

    private fun createSelfieSavedNotificationChannel(context: Context, manager: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = context.getString(R.string.selfie_saved_notification_channel_name)
            val channel = NotificationChannel(
                SELFIE_SAVED_CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows an alert when an intruder selfie is saved to the device."
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}