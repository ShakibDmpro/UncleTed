package com.hamoon.uncleted.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hamoon.uncleted.MainActivity
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.AISecurityOrchestrator
import com.hamoon.uncleted.util.BehavioralAnalysisEngine
import com.hamoon.uncleted.util.QuantumSecurityLayer
import com.hamoon.uncleted.util.ShakeDetector
import kotlinx.coroutines.*

class MonitoringService : LifecycleService(), SensorEventListener {

    // ### FIX: Make properties lateinit as they will be initialized asynchronously ###
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetector
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "UncleTedMonitoringChannel"
    }

    override fun onCreate() {
        super.onCreate()
        // ### FIX: Defer all heavy initialization to a background thread ###
        // The original code performed setup synchronously on the main thread,
        // causing the UI to freeze (skipped frames) during app startup.
        // All initialization is now moved to a single background task.
        lifecycleScope.launch(Dispatchers.IO) {
            initializeComponents()
        }
    }

    /**
     * ### FIX: Consolidated, asynchronous initialization method ###
     * This function now handles all setup for the service on a background thread,
     * preventing any blocking of the main UI thread. Proper backgrounding allows the
     * OS to schedule work much more efficiently.
     */
    private suspend fun initializeComponents() {
        try {
            // --- Shake Detector Initialization (previously on main thread) ---
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val sensitivity = SecurityPreferences.getShakeSensitivity(this@MonitoringService)

            shakeDetector = ShakeDetector(
                listener = object : ShakeDetector.OnShakeListener {
                    override fun onShake(count: Int) {
                        if (SecurityPreferences.isShakeToPanicEnabled(this@MonitoringService)) {
                            Log.d("MonitoringService", "Shake detected! Triggering panic.")
                            PanicActionService.trigger(this@MonitoringService, "SHAKE_TRIGGERED", PanicActionService.Severity.HIGH)
                        }
                    }
                },
                sensitivityLevel = sensitivity
            )

            // Registering a sensor listener must be done on the main thread.
            withContext(Dispatchers.Main) {
                if (accelerometer != null) {
                    sensorManager.registerListener(this@MonitoringService, accelerometer, SensorManager.SENSOR_DELAY_UI)
                } else {
                    Log.e("MonitoringService", "Accelerometer not available on this device.")
                }
            }

            // --- Advanced Security Initialization ---
            // Initialize Behavioral Analysis Engine
            BehavioralAnalysisEngine.initialize(this@MonitoringService)
            // Observers must be added on the main thread.
            withContext(Dispatchers.Main) {
                lifecycle.addObserver(BehavioralAnalysisEngine)
            }

            // Initialize Quantum Security Layer
            QuantumSecurityLayer.initializeQuantumSecurity(this@MonitoringService)

            // Initialize AI Security Orchestrator
            AISecurityOrchestrator.initialize(this@MonitoringService)

            Log.i("MonitoringService", "All monitoring components initialized successfully on a background thread.")

        } catch (e: Exception) {
            Log.e("MonitoringService", "Failed to initialize monitoring components", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d("MonitoringService", "MonitoringService started.")
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE)
            }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_shield_check_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // ### FIX: Check if shakeDetector is initialized before using it ###
        if (::shakeDetector.isInitialized && event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            shakeDetector.updateShake(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    override fun onDestroy() {
        // Check for initialization before trying to unregister
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }
        serviceScope.cancel()

        // Shutdown advanced security components
        try {
            AISecurityOrchestrator.shutdown()
            QuantumSecurityLayer.shutdown()
        } catch (e: Exception) {
            Log.e("MonitoringService", "Error shutting down advanced security", e)
        }

        Log.d("MonitoringService", "MonitoringService stopped.")
        super.onDestroy()
    }
}