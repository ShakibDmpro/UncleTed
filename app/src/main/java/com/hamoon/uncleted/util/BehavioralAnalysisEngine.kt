package com.hamoon.uncleted.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*
import java.util.*

object BehavioralAnalysisEngine : DefaultLifecycleObserver, SensorEventListener {

    private const val TAG = "BehavioralAnalysis"
    private const val LEARNING_PERIOD_DAYS = 7
    private const val MIN_SAMPLES_FOR_ANALYSIS = 50
    private const val ANOMALY_THRESHOLD = 0.75 // 75% deviation from normal

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null

    private val _behavioralState = MutableStateFlow(BehavioralState.LEARNING)
    val behavioralState: StateFlow<BehavioralState> = _behavioralState

    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Behavioral pattern data
    private val typingPatterns = mutableListOf<TypingPattern>()
    private val walkingPatterns = mutableListOf<WalkingPattern>()
    private val phoneHoldingPatterns = mutableListOf<PhoneHoldingPattern>()
    private val appUsagePatterns = mutableListOf<AppUsagePattern>()
    private val touchPressurePatterns = mutableListOf<TouchPressurePattern>()

    // Real-time sensor data
    private var currentAcceleration = FloatArray(3)
    private var currentGyroscope = FloatArray(3)
    private var currentMagnetometer = FloatArray(3)
    private var lastSensorUpdate = 0L

    enum class BehavioralState {
        LEARNING, ANALYZING, ANOMALY_DETECTED, USER_VERIFIED, INTRUDER_CONFIRMED
    }

    data class TypingPattern(
        val dwellTimes: List<Long>, // Time key is held down
        val flightTimes: List<Long>, // Time between key releases
        val pressure: List<Float>,
        val touchArea: List<Float>,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class WalkingPattern(
        val stepFrequency: Double,
        val accelerationMagnitude: List<Double>,
        val stepVariability: Double,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class PhoneHoldingPattern(
        val orientationAngles: List<Double>,
        val gripStability: Double,
        val averageTilt: Double,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class AppUsagePattern(
        val appSequence: List<String>,
        val usageDuration: List<Long>,
        val transitionTimes: List<Long>,
        val timeOfDay: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class TouchPressurePattern(
        val averagePressure: Double,
        val pressureVariance: Double,
        val touchSize: Double,
        val touchDuration: Long,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class BehavioralProfile(
        val userId: String,
        val confidence: Double,
        val patterns: Map<String, Any>,
        val lastUpdated: Long,
        val sampleCount: Int
    )

    fun initialize(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        loadStoredPatterns(context)
        startBehavioralAnalysis(context)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        startSensorMonitoring()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        stopSensorMonitoring()
        analysisScope.cancel()
    }

    private fun startSensorMonitoring() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun stopSensorMonitoring() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            val currentTime = System.currentTimeMillis()

            when (sensorEvent.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(sensorEvent.values, 0, currentAcceleration, 0, 3)
                    analyzeMovementPattern(currentTime)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    System.arraycopy(sensorEvent.values, 0, currentGyroscope, 0, 3)
                    analyzePhoneHoldingPattern(currentTime)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(sensorEvent.values, 0, currentMagnetometer, 0, 3)
                }
            }

            lastSensorUpdate = currentTime
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle sensor accuracy changes if needed
    }

    private fun startBehavioralAnalysis(context: Context) {
        analysisScope.launch {
            while (isActive) {
                performBehavioralAnalysis(context)
                delay(30000) // Analyze every 30 seconds
            }
        }
    }

    private suspend fun performBehavioralAnalysis(context: Context) = withContext(Dispatchers.Default) {
        if (isInLearningPhase(context)) {
            _behavioralState.value = BehavioralState.LEARNING
            return@withContext
        }

        _behavioralState.value = BehavioralState.ANALYZING

        val currentProfile = generateCurrentProfile(context)
        val storedProfile = loadStoredProfile(context)

        if (storedProfile != null) {
            val similarity = calculateProfileSimilarity(currentProfile, storedProfile)

            Log.d(TAG, "Behavioral similarity: ${(similarity * 100).toInt()}%")

            if (similarity < ANOMALY_THRESHOLD) {
                handleBehavioralAnomaly(context, similarity, currentProfile, storedProfile)
            } else {
                // Update stored profile with new data
                updateStoredProfile(context, currentProfile)
            }
        }
    }

    private fun isInLearningPhase(context: Context): Boolean {
        val firstRun = SecurityPreferences.getFirstRunTimestamp(context)
        val daysSinceFirstRun = (System.currentTimeMillis() - firstRun) / (24 * 60 * 60 * 1000)
        val totalSamples = getTotalSampleCount()

        return daysSinceFirstRun < LEARNING_PERIOD_DAYS || totalSamples < MIN_SAMPLES_FOR_ANALYSIS
    }

    private fun getTotalSampleCount(): Int {
        return typingPatterns.size + walkingPatterns.size + phoneHoldingPatterns.size +
                appUsagePatterns.size + touchPressurePatterns.size
    }

    private fun analyzeMovementPattern(timestamp: Long) {
        val magnitude = sqrt(
            currentAcceleration[0].pow(2) +
                    currentAcceleration[1].pow(2) +
                    currentAcceleration[2].pow(2)
        ).toDouble()

        // Detect walking pattern
        analysisScope.launch {
            val walkingPattern = detectWalkingPattern(magnitude, timestamp)
            walkingPattern?.let {
                walkingPatterns.add(it)
                if (walkingPatterns.size > 1000) {
                    walkingPatterns.removeFirst()
                }
            }
        }
    }

    private fun analyzePhoneHoldingPattern(timestamp: Long) {
        val rotationMagnitude = sqrt(
            currentGyroscope[0].pow(2) +
                    currentGyroscope[1].pow(2) +
                    currentGyroscope[2].pow(2)
        ).toDouble()

        analysisScope.launch {
            val holdingPattern = detectPhoneHoldingPattern(rotationMagnitude, timestamp)
            holdingPattern?.let {
                phoneHoldingPatterns.add(it)
                if (phoneHoldingPatterns.size > 500) {
                    phoneHoldingPatterns.removeFirst()
                }
            }
        }
    }

    private suspend fun detectWalkingPattern(magnitude: Double, timestamp: Long): WalkingPattern? = withContext(Dispatchers.Default) {
        // Simple step detection algorithm
        if (magnitude > 12.0) { // Threshold for step detection
            val recentMagnitudes = getRecentMagnitudes(timestamp)
            if (recentMagnitudes.size >= 10) {
                val frequency = calculateStepFrequency(recentMagnitudes)
                val variability = calculateStepVariability(recentMagnitudes)

                return@withContext WalkingPattern(
                    stepFrequency = frequency,
                    accelerationMagnitude = recentMagnitudes,
                    stepVariability = variability,
                    timestamp = timestamp
                )
            }
        }
        null
    }

    private suspend fun detectPhoneHoldingPattern(rotation: Double, timestamp: Long): PhoneHoldingPattern? = withContext(Dispatchers.Default) {
        val recentRotations = getRecentRotations(timestamp)
        if (recentRotations.size >= 20) {
            val angles = recentRotations.map { atan2(it, 1.0) * 180 / PI }
            val stability = calculateGripStability(recentRotations)
            val averageTilt = angles.average()

            return@withContext PhoneHoldingPattern(
                orientationAngles = angles,
                gripStability = stability,
                averageTilt = averageTilt,
                timestamp = timestamp
            )
        }
        null
    }

    fun recordTypingPattern(dwellTimes: List<Long>, flightTimes: List<Long>, pressures: List<Float>, touchAreas: List<Float>) {
        val pattern = TypingPattern(dwellTimes, flightTimes, pressures, touchAreas)
        typingPatterns.add(pattern)

        if (typingPatterns.size > 200) {
            typingPatterns.removeFirst()
        }

        Log.d(TAG, "Recorded typing pattern: ${dwellTimes.size} keystrokes")
    }

    fun recordAppUsagePattern(apps: List<String>, durations: List<Long>, transitions: List<Long>) {
        val timeOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val pattern = AppUsagePattern(apps, durations, transitions, timeOfDay)
        appUsagePatterns.add(pattern)

        if (appUsagePatterns.size > 100) {
            appUsagePatterns.removeFirst()
        }

        Log.d(TAG, "Recorded app usage pattern: ${apps.size} apps")
    }

    fun recordTouchPressurePattern(pressure: Double, variance: Double, size: Double, duration: Long) {
        val pattern = TouchPressurePattern(pressure, variance, size, duration)
        touchPressurePatterns.add(pattern)

        if (touchPressurePatterns.size > 300) {
            touchPressurePatterns.removeFirst()
        }
    }

    private fun generateCurrentProfile(context: Context): BehavioralProfile {
        val patterns = mutableMapOf<String, Any>()

        // Typing patterns analysis
        if (typingPatterns.isNotEmpty()) {
            patterns["typing"] = analyzeTypingPatterns()
        }

        // Walking patterns analysis
        if (walkingPatterns.isNotEmpty()) {
            patterns["walking"] = analyzeWalkingPatterns()
        }

        // Phone holding patterns analysis
        if (phoneHoldingPatterns.isNotEmpty()) {
            patterns["holding"] = analyzeHoldingPatterns()
        }

        // App usage patterns analysis
        if (appUsagePatterns.isNotEmpty()) {
            patterns["app_usage"] = analyzeAppUsagePatterns()
        }

        // Touch pressure patterns analysis
        if (touchPressurePatterns.isNotEmpty()) {
            patterns["touch"] = analyzeTouchPatterns()
        }

        return BehavioralProfile(
            userId = "primary_user",
            // ### FIX 2: Pass the context to the corrected function ###
            confidence = calculateProfileConfidence(context),
            patterns = patterns,
            lastUpdated = System.currentTimeMillis(),
            sampleCount = getTotalSampleCount()
        )
    }

    private fun analyzeTypingPatterns(): Map<String, Double> {
        val recentPatterns = typingPatterns.takeLast(50)

        val avgDwellTime = recentPatterns.flatMap { it.dwellTimes }.average()
        val avgFlightTime = recentPatterns.flatMap { it.flightTimes }.average()
        val avgPressure = recentPatterns.flatMap { it.pressure }.average().toDouble()
        val avgTouchArea = recentPatterns.flatMap { it.touchArea }.average().toDouble()

        val dwellVariance = calculateVariance(recentPatterns.flatMap { it.dwellTimes }.map { it.toDouble() })
        val flightVariance = calculateVariance(recentPatterns.flatMap { it.flightTimes }.map { it.toDouble() })

        return mapOf(
            "avg_dwell_time" to avgDwellTime,
            "avg_flight_time" to avgFlightTime,
            "avg_pressure" to avgPressure,
            "avg_touch_area" to avgTouchArea,
            "dwell_variance" to dwellVariance,
            "flight_variance" to flightVariance
        )
    }

    private fun analyzeWalkingPatterns(): Map<String, Double> {
        val recentPatterns = walkingPatterns.takeLast(30)

        val avgFrequency = recentPatterns.map { it.stepFrequency }.average()
        val avgVariability = recentPatterns.map { it.stepVariability }.average()
        val frequencyConsistency = 1.0 - calculateVariance(recentPatterns.map { it.stepFrequency })

        return mapOf(
            "avg_step_frequency" to avgFrequency,
            "avg_variability" to avgVariability,
            "frequency_consistency" to frequencyConsistency
        )
    }

    private fun analyzeHoldingPatterns(): Map<String, Double> {
        val recentPatterns = phoneHoldingPatterns.takeLast(20)

        val avgStability = recentPatterns.map { it.gripStability }.average()
        val avgTilt = recentPatterns.map { it.averageTilt }.average()
        val tiltConsistency = 1.0 - calculateVariance(recentPatterns.map { it.averageTilt })

        return mapOf(
            "avg_grip_stability" to avgStability,
            "avg_tilt" to avgTilt,
            "tilt_consistency" to tiltConsistency
        )
    }

    private fun analyzeAppUsagePatterns(): Map<String, Double> {
        val recentPatterns = appUsagePatterns.takeLast(20)

        val avgSessionDuration = recentPatterns.flatMap { it.usageDuration }.average()
        val avgTransitionTime = recentPatterns.flatMap { it.transitionTimes }.average()
        val preferredTimeOfDay = recentPatterns.map { it.timeOfDay }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.toDouble() ?: 12.0

        return mapOf(
            "avg_session_duration" to avgSessionDuration,
            "avg_transition_time" to avgTransitionTime,
            "preferred_time_of_day" to preferredTimeOfDay
        )
    }

    private fun analyzeTouchPatterns(): Map<String, Double> {
        val recentPatterns = touchPressurePatterns.takeLast(50)

        val avgPressure = recentPatterns.map { it.averagePressure }.average()
        val avgVariance = recentPatterns.map { it.pressureVariance }.average()
        val avgSize = recentPatterns.map { it.touchSize }.average()
        val avgDuration = recentPatterns.map { it.touchDuration }.average()

        return mapOf(
            "avg_pressure" to avgPressure,
            "avg_pressure_variance" to avgVariance,
            "avg_touch_size" to avgSize,
            "avg_touch_duration" to avgDuration
        )
    }

    // ### FIX 1: Add the missing 'context: Context' parameter to the function signature ###
    private fun calculateProfileConfidence(context: Context): Double {
        val sampleCount = getTotalSampleCount()
        // ### FIX 3: Replace the invalid code with a correct call using the context ###
        val daysSinceFirstRun = (System.currentTimeMillis() - SecurityPreferences.getFirstRunTimestamp(context)) / (24 * 60 * 60 * 1000)

        val sampleConfidence = minOf(sampleCount.toDouble() / MIN_SAMPLES_FOR_ANALYSIS, 1.0)
        val timeConfidence = minOf(daysSinceFirstRun.toDouble() / LEARNING_PERIOD_DAYS, 1.0)

        return (sampleConfidence + timeConfidence) / 2.0
    }

    private fun calculateProfileSimilarity(current: BehavioralProfile, stored: BehavioralProfile): Double {
        var totalSimilarity = 0.0
        var patternCount = 0

        for ((patternType, currentData) in current.patterns) {
            val storedData = stored.patterns[patternType]
            if (storedData != null && currentData is Map<*, *> && storedData is Map<*, *>) {
                val similarity = calculatePatternSimilarity(currentData, storedData)
                totalSimilarity += similarity
                patternCount++
            }
        }

        return if (patternCount > 0) totalSimilarity / patternCount else 0.0
    }

    private fun calculatePatternSimilarity(pattern1: Map<*, *>, pattern2: Map<*, *>): Double {
        val commonKeys = pattern1.keys.intersect(pattern2.keys)
        if (commonKeys.isEmpty()) return 0.0

        var totalSimilarity = 0.0

        for (key in commonKeys) {
            val value1 = pattern1[key] as? Double ?: continue
            val value2 = pattern2[key] as? Double ?: continue

            val similarity = 1.0 - abs(value1 - value2) / maxOf(abs(value1), abs(value2), 1.0)
            totalSimilarity += similarity
        }

        return totalSimilarity / commonKeys.size
    }

    private suspend fun handleBehavioralAnomaly(
        context: Context,
        similarity: Double,
        currentProfile: BehavioralProfile,
        storedProfile: BehavioralProfile
    ) = withContext(Dispatchers.Main) {
        _behavioralState.value = BehavioralState.ANOMALY_DETECTED

        val severityLevel = when {
            similarity < 0.3 -> PanicActionService.Severity.CRITICAL
            similarity < 0.5 -> PanicActionService.Severity.HIGH
            similarity < 0.7 -> PanicActionService.Severity.MEDIUM
            else -> PanicActionService.Severity.LOW
        }

        Log.w(TAG, "Behavioral anomaly detected! Similarity: ${(similarity * 100).toInt()}%")
        EventLogger.log(context, "BEHAVIORAL ANOMALY: Similarity ${(similarity * 100).toInt()}% (Threshold: ${(ANOMALY_THRESHOLD * 100).toInt()}%)")

        // Store anomaly details for investigation
        storeBehavioralAnomaly(context, similarity, currentProfile, storedProfile)

        // Trigger security response
        PanicActionService.trigger(context, "BEHAVIORAL_ANOMALY", severityLevel)

        // Start enhanced monitoring
        startEnhancedMonitoring(context)
    }

    private fun startEnhancedMonitoring(context: Context) {
        analysisScope.launch {
            // Increase analysis frequency
            repeat(20) { // Monitor for 10 minutes with 30-second intervals
                delay(30000)
                performBehavioralAnalysis(context)
            }
        }
    }

    private fun storeBehavioralAnomaly(context: Context, similarity: Double, current: BehavioralProfile, stored: BehavioralProfile) {
        val anomalyData = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("similarity_score", similarity)
            put("threshold", ANOMALY_THRESHOLD)
            put("current_profile", JSONObject(current.patterns))
            put("stored_profile", JSONObject(stored.patterns))
        }

        SecurityPreferences.addBehavioralAnomaly(context, anomalyData.toString())
    }

    private fun loadStoredPatterns(context: Context) {
        // Load patterns from secure storage
        // Implementation would read from SecurityPreferences
    }

    private fun loadStoredProfile(context: Context): BehavioralProfile? {
        val profileJson = SecurityPreferences.getBehavioralProfile(context)
        return if (profileJson.isNotEmpty()) {
            // Parse JSON and reconstruct profile
            try {
                val json = JSONObject(profileJson)
                BehavioralProfile(
                    userId = json.getString("userId"),
                    confidence = json.getDouble("confidence"),
                    patterns = parsePatterns(json.getJSONObject("patterns")),
                    lastUpdated = json.getLong("lastUpdated"),
                    sampleCount = json.getInt("sampleCount")
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse stored behavioral profile", e)
                null
            }
        } else null
    }

    private fun updateStoredProfile(context: Context, profile: BehavioralProfile) {
        val profileJson = JSONObject().apply {
            put("userId", profile.userId)
            put("confidence", profile.confidence)
            put("patterns", JSONObject(profile.patterns))
            put("lastUpdated", profile.lastUpdated)
            put("sampleCount", profile.sampleCount)
        }

        SecurityPreferences.setBehavioralProfile(context, profileJson.toString())
    }

    private fun parsePatterns(json: JSONObject): Map<String, Any> {
        val patterns = mutableMapOf<String, Any>()
        val keys = json.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            patterns[key] = value
        }

        return patterns
    }

    // Helper functions
    private fun getRecentMagnitudes(timestamp: Long): List<Double> {
        // Implementation to get recent acceleration magnitudes
        return emptyList()
    }

    private fun getRecentRotations(timestamp: Long): List<Double> {
        // Implementation to get recent rotation data
        return emptyList()
    }

    private fun calculateStepFrequency(magnitudes: List<Double>): Double {
        // Implementation for step frequency calculation
        return 2.0 // Default walking frequency (steps per second)
    }

    private fun calculateStepVariability(magnitudes: List<Double>): Double {
        return calculateVariance(magnitudes)
    }

    private fun calculateGripStability(rotations: List<Double>): Double {
        return 1.0 - calculateVariance(rotations)
    }

    private fun calculateVariance(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.map { (it - mean).pow(2) }.average()
    }
}

// Extensions for SecurityPreferences
private fun SecurityPreferences.getFirstRunTimestamp(context: Context): Long =
    getInstance(context).getLong("FIRST_RUN_TIMESTAMP", System.currentTimeMillis())

private fun SecurityPreferences.getBehavioralProfile(context: Context): String =
    getInstance(context).getString("BEHAVIORAL_PROFILE", "") ?: ""

private fun SecurityPreferences.setBehavioralProfile(context: Context, profile: String) =
    getInstance(context).edit().putString("BEHAVIORAL_PROFILE", profile).apply()

private fun SecurityPreferences.addBehavioralAnomaly(context: Context, anomaly: String) {
    val existingAnomalies = getInstance(context).getStringSet("BEHAVIORAL_ANOMALIES", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    existingAnomalies.add(anomaly)

    // Keep only last 50 anomalies
    if (existingAnomalies.size > 50) {
        val sortedAnomalies = existingAnomalies.toList().sortedBy {
            try {
                JSONObject(it).getLong("timestamp")
            } catch (e: Exception) { 0L }
        }
        existingAnomalies.clear()
        existingAnomalies.addAll(sortedAnomalies.takeLast(50))
    }

    getInstance(context).edit().putStringSet("BEHAVIORAL_ANOMALIES", existingAnomalies).apply()
}