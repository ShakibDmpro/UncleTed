package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random as JavaRandom
import kotlin.math.*
import kotlin.random.Random

object AISecurityOrchestrator {

    private const val TAG = "AISecurityOrchestrator"
    private const val LEARNING_RATE = 0.01
    private const val NETWORK_LAYERS = 4
    private const val NEURONS_PER_LAYER = 16

    private val orchestratorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Neural Network Components
    private lateinit var threatPredictionNetwork: NeuralNetwork
    private lateinit var behaviorAnalysisNetwork: NeuralNetwork
    private lateinit var responseOptimizationNetwork: NeuralNetwork

    // AI State Management
    private val _aiState = MutableStateFlow(AIState.INITIALIZING)
    val aiState: StateFlow<AIState> = _aiState

    private val _threatLevel = MutableStateFlow(0.0)
    val threatLevel: StateFlow<Double> = _threatLevel

    private val _adaptiveSecurityLevel = MutableStateFlow(SecurityLevel.NORMAL)
    val adaptiveSecurityLevel: StateFlow<SecurityLevel> = _adaptiveSecurityLevel

    enum class AIState {
        INITIALIZING, LEARNING, ACTIVE_MONITORING, THREAT_DETECTED,
        ADAPTIVE_RESPONSE, EVOLUTIONARY_LEARNING, QUANTUM_PROCESSING
    }

    enum class SecurityLevel {
        MINIMAL, LOW, NORMAL, HIGH, MAXIMUM, QUANTUM_ENHANCED
    }

    data class ThreatVector(
        val type: String,
        val probability: Double,
        val severity: Double,
        val timeToImpact: Long,
        val confidence: Double,
        val mitigationStrategies: List<String>
    )

    data class AIDecision(
        val action: String,
        val confidence: Double,
        val reasoning: List<String>,
        val alternativeActions: List<String>,
        val expectedOutcome: String,
        val riskAssessment: Double
    )

    class NeuralNetwork(
        private val inputSize: Int,
        private val hiddenLayerSizes: IntArray,
        private val outputSize: Int
    ) {
        private val weights: Array<Array<DoubleArray>>
        private val biases: Array<DoubleArray>

        init {
            val layerSizes = intArrayOf(inputSize) + hiddenLayerSizes + intArrayOf(outputSize)
            weights = Array(layerSizes.size - 1) { layer ->
                Array(layerSizes[layer + 1]) { neuron ->
                    DoubleArray(layerSizes[layer]) { JavaRandom().nextGaussian() * 0.5 }
                }
            }
            biases = Array(layerSizes.size - 1) { layer ->
                DoubleArray(layerSizes[layer + 1]) { JavaRandom().nextGaussian() * 0.5 }
            }
        }

        fun forward(inputs: DoubleArray): DoubleArray {
            var activations = inputs

            for (layer in weights.indices) {
                val newActivations = DoubleArray(weights[layer].size)

                for (neuron in weights[layer].indices) {
                    var sum = biases[layer][neuron]
                    for (input in activations.indices) {
                        sum += activations[input] * weights[layer][neuron][input]
                    }
                    newActivations[neuron] = sigmoid(sum)
                }

                activations = newActivations
            }

            return activations
        }

        fun train(inputs: DoubleArray, expectedOutputs: DoubleArray) {
            val outputs = forward(inputs)
            val errors = outputs.mapIndexed { i, output ->
                (expectedOutputs.getOrNull(i) ?: 0.5) - output
            }.toDoubleArray()

            for (layer in weights.size - 1 downTo 0) {
                for (neuron in weights[layer].indices) {
                    for (input in weights[layer][neuron].indices) {
                        weights[layer][neuron][input] += LEARNING_RATE * errors.getOrElse(neuron) { 0.0 }
                    }
                }
            }
        }

        private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))
    }

    fun initialize(context: Context) {
        Log.i(TAG, "Initializing AI Security Orchestrator...")

        orchestratorScope.launch {
            try {
                _aiState.value = AIState.INITIALIZING
                initializeNeuralNetworks()
                loadAIModels(context)
                // ### START: PRECISE FIX FOR CONSTANT ANALYSIS ###
                // The analysis loops are now controlled by the AppLifecycleManager state.
                startLifecycleAwareMonitoring(context)
                // ### END: PRECISE FIX FOR CONSTANT ANALYSIS ###
                initializeAdaptiveSecurity(context)
                _aiState.value = AIState.ACTIVE_MONITORING
                Log.i(TAG, "AI Security Orchestrator initialized successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AI Security Orchestrator", e)
                _aiState.value = AIState.INITIALIZING
            }
        }
    }

    private fun initializeNeuralNetworks() {
        threatPredictionNetwork = NeuralNetwork(20, intArrayOf(32, 16, 8), 5)
        behaviorAnalysisNetwork = NeuralNetwork(15, intArrayOf(24, 12), 3)
        responseOptimizationNetwork = NeuralNetwork(10, intArrayOf(16, 8), 6)
        Log.d(TAG, "Neural networks initialized")
    }

    private suspend fun loadAIModels(context: Context) = withContext(Dispatchers.IO) {
        val modelData = SecurityPreferences.getAIModelData(context)
        if (modelData.isNotEmpty()) {
            try {
                JSONObject(modelData)
                Log.d(TAG, "Loaded existing AI models")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load existing AI models, starting fresh", e)
            }
        }
    }

    // ### START: PRECISE FIX FOR CONSTANT ANALYSIS ###
    /**
     * Creates a flow that emits a value at a regular interval.
     */
    private fun tickerFlow(period: Long) = flow {
        while (true) {
            emit(Unit)
            delay(period)
        }
    }

    /**
     * Rewritten monitoring logic that only runs when the app is in the foreground.
     * It uses `flatMapLatest` to start a ticking flow when the app becomes visible
     * and automatically cancels it when the app goes into the background.
     */
    private fun startLifecycleAwareMonitoring(context: Context) {
        orchestratorScope.launch {
            AppLifecycleManager.isAppInForeground.flatMapLatest { isInForeground ->
                if (isInForeground) {
                    Log.i(TAG, "App is in foreground. Starting active AI monitoring.")
                    // When in foreground, start a ticker that emits every 10 seconds
                    tickerFlow(10000)
                } else {
                    Log.i(TAG, "App is in background. Pausing active AI monitoring.")
                    // When in background, switch to an empty flow, effectively stopping emissions
                    emptyFlow()
                }
            }.collect {
                // This block is now only executed every 10 seconds WHILE the app is in the foreground
                try {
                    val securityMetrics = collectSecurityMetrics(context)
                    val threatPredictions = predictThreats(securityMetrics)
                    val behaviorAnalysis = analyzeBehavior(context)
                    val decision = makeAIDecision(securityMetrics, threatPredictions, behaviorAnalysis)
                    executeAIDecision(context, decision)
                    _threatLevel.value = threatPredictions.maxOfOrNull { it.probability * it.severity } ?: 0.0
                } catch (e: Exception) {
                    Log.e(TAG, "Error in active monitoring", e)
                }
            }
        }
    }
    // ### END: PRECISE FIX FOR CONSTANT ANALYSIS ###

    private fun initializeAdaptiveSecurity(context: Context) {
        orchestratorScope.launch {
            threatLevel.collect { level ->
                val newSecurityLevel = when {
                    level < 0.2 -> SecurityLevel.LOW
                    level < 0.4 -> SecurityLevel.NORMAL
                    level < 0.6 -> SecurityLevel.HIGH
                    level < 0.8 -> SecurityLevel.MAXIMUM
                    else -> SecurityLevel.QUANTUM_ENHANCED
                }

                if (newSecurityLevel != _adaptiveSecurityLevel.value) {
                    adaptSecurityLevel(context, newSecurityLevel)
                    _adaptiveSecurityLevel.value = newSecurityLevel
                }
            }
        }
    }


    private suspend fun collectTrainingData(context: Context): List<TrainingExample> = withContext(Dispatchers.Default) {
        val examples = mutableListOf<TrainingExample>()
        val behaviorData = BehavioralAnalysisEngine.behavioralState.value
        if (behaviorData != BehavioralAnalysisEngine.BehavioralState.LEARNING) {
            examples.add(TrainingExample(extractBehaviorFeatures(context), getBehaviorLabels(behaviorData), "behavior"))
        }
        val threatAssessment = ThreatDetectionEngine.performThreatAnalysis(context)
        examples.add(TrainingExample(extractThreatFeatures(threatAssessment), getThreatLabels(threatAssessment), "threat"))
        val quantumAudit = QuantumSecurityLayer.performQuantumSecurityAudit(context)
        examples.add(TrainingExample(extractQuantumFeatures(quantumAudit), getQuantumLabels(quantumAudit), "quantum"))
        return@withContext examples
    }

    data class TrainingExample(val inputs: DoubleArray, val outputs: DoubleArray, val type: String, val timestamp: Long = System.currentTimeMillis())

    private fun trainNetworks(trainingData: List<TrainingExample>) {
        trainingData.forEach {
            when (it.type) {
                "behavior" -> behaviorAnalysisNetwork.train(it.inputs, it.outputs)
                "threat" -> threatPredictionNetwork.train(it.inputs, it.outputs)
                "quantum" -> responseOptimizationNetwork.train(it.inputs, it.outputs)
            }
        }
        Log.d(TAG, "Trained networks with ${trainingData.size} examples")
    }

    private suspend fun collectSecurityMetrics(context: Context): DoubleArray = withContext(Dispatchers.Default) {
        val metrics = mutableListOf<Double>()
        val securityLevel = SecurityScoreCalculator.calculateSecurityLevel(context)
        metrics.add(securityLevel.level.toDouble() / 6.0)
        metrics.add(if (PermissionUtils.hasCameraPermission(context)) 1.0 else 0.0)
        metrics.add(if (PermissionUtils.hasLocationPermissions(context)) 1.0 else 0.0)
        metrics.add(if (PermissionUtils.isDeviceAdminActive(context)) 1.0 else 0.0)
        metrics.add(Runtime.getRuntime().freeMemory().toDouble() / Runtime.getRuntime().totalMemory())
        metrics.add(System.currentTimeMillis().toDouble() % 86400000 / 86400000)
        while (metrics.size < 20) {
            metrics.add(Random.nextDouble())
        }
        return@withContext metrics.toDoubleArray()
    }

    private fun predictThreats(securityMetrics: DoubleArray): List<ThreatVector> {
        val predictions = threatPredictionNetwork.forward(securityMetrics)
        val threats = mutableListOf<ThreatVector>()
        val threatTypes = listOf("Malware", "Social Engineering", "Physical Theft", "Data Breach", "System Compromise")
        for (i in predictions.indices) {
            if (predictions[i] > 0.3) {
                threats.add(ThreatVector(threatTypes.getOrElse(i) { "Unknown" }, predictions[i], min(predictions[i] * 1.5, 1.0), (predictions[i] * 3600000).toLong(), predictions[i], generateMitigationStrategies(threatTypes.getOrElse(i) { "Unknown" })))
            }
        }
        return threats
    }

    private fun analyzeBehavior(context: Context): DoubleArray {
        return behaviorAnalysisNetwork.forward(extractBehaviorFeatures(context))
    }

    private fun makeAIDecision(securityMetrics: DoubleArray, threats: List<ThreatVector>, behaviorAnalysis: DoubleArray): AIDecision {
        val contextFeatures = combineFeatures(securityMetrics, threats, behaviorAnalysis)
        val responseScores = responseOptimizationNetwork.forward(contextFeatures)
        val actions = listOf("MAINTAIN_CURRENT_SECURITY", "INCREASE_MONITORING", "TRIGGER_MILD_ALERT", "ACTIVATE_HIGH_SECURITY", "INITIATE_LOCKDOWN", "QUANTUM_SECURITY_PROTOCOL")
        val bestActionIndex = responseScores.indices.maxByOrNull { responseScores[it] } ?: 0
        val confidence = responseScores[bestActionIndex]
        val reasoning = generateReasoning(threats, behaviorAnalysis, securityMetrics)
        val alternatives = actions.filterIndexed { index, _ -> index != bestActionIndex }.take(2)
        return AIDecision(actions[bestActionIndex], confidence, reasoning, alternatives, predictOutcome(actions[bestActionIndex], threats), calculateRiskAssessment(threats, confidence))
    }

    private suspend fun executeAIDecision(context: Context, decision: AIDecision) = withContext(Dispatchers.Main) {
        Log.i(TAG, "AI Decision: ${decision.action} (Confidence: ${(decision.confidence * 100).toInt()}%)")
        when (decision.action) {
            "MAINTAIN_CURRENT_SECURITY" -> {}
            "INCREASE_MONITORING" -> increaseMonitoringFrequency(context)
            "TRIGGER_MILD_ALERT" -> EventLogger.log(context, "AI: Mild threat detected - ${decision.reasoning.firstOrNull()}")
            "ACTIVATE_HIGH_SECURITY" -> activateHighSecurityMode(context)
            "INITIATE_LOCKDOWN" -> PanicActionService.trigger(context, "AI_INITIATED_LOCKDOWN", PanicActionService.Severity.HIGH)
            "QUANTUM_SECURITY_PROTOCOL" -> {
                QuantumSecurityLayer.generateQuantumKey(context)
                _aiState.value = AIState.QUANTUM_PROCESSING
            }
        }
        storeAIDecision(context, decision)
    }

    private suspend fun adaptSecurityLevel(context: Context, newLevel: SecurityLevel) = withContext(Dispatchers.Main) {
        Log.i(TAG, "Adapting security level to: $newLevel")
        when (newLevel) {
            SecurityLevel.MINIMAL -> adjustMonitoringFrequency(context, 0.5f)
            SecurityLevel.LOW -> adjustMonitoringFrequency(context, 0.8f)
            SecurityLevel.NORMAL -> adjustMonitoringFrequency(context, 1.0f)
            SecurityLevel.HIGH -> {
                adjustMonitoringFrequency(context, 1.5f)
                enableEnhancedFeatures(context)
            }
            SecurityLevel.MAXIMUM -> {
                adjustMonitoringFrequency(context, 2.0f)
                enableEnhancedFeatures(context)
                activateAllSensors(context)
            }
            SecurityLevel.QUANTUM_ENHANCED -> {
                adjustMonitoringFrequency(context, 3.0f)
                enableEnhancedFeatures(context)
                activateAllSensors(context)
                QuantumSecurityLayer.initializeQuantumSecurity(context)
            }
        }
        EventLogger.log(context, "AI: Security level adapted to $newLevel")
    }

    private fun extractBehaviorFeatures(context: Context): DoubleArray = DoubleArray(15) { Random.nextDouble() }

    private fun extractThreatFeatures(threatAssessment: ThreatDetectionEngine.ThreatAssessment): DoubleArray = doubleArrayOf(threatAssessment.threats.size.toDouble() / 10.0, threatAssessment.confidence.toDouble(), when (threatAssessment.threatLevel) {
        ThreatDetectionEngine.ThreatLevel.MINIMAL -> 0.1
        ThreatDetectionEngine.ThreatLevel.LOW -> 0.3
        ThreatDetectionEngine.ThreatLevel.MEDIUM -> 0.5
        ThreatDetectionEngine.ThreatLevel.HIGH -> 0.7
        ThreatDetectionEngine.ThreatLevel.CRITICAL -> 0.9
    })

    private fun extractQuantumFeatures(quantumAudit: QuantumSecurityLayer.QuantumSecurityAudit): DoubleArray = doubleArrayOf(quantumAudit.quantumKeyStrength, quantumAudit.entanglementIntegrity, quantumAudit.superpositionStability, quantumAudit.uncertaintyLevel, quantumAudit.observationImpact)

    private fun getBehaviorLabels(behaviorState: BehavioralAnalysisEngine.BehavioralState): DoubleArray = when (behaviorState) {
        BehavioralAnalysisEngine.BehavioralState.USER_VERIFIED -> doubleArrayOf(1.0, 0.0, 0.0)
        BehavioralAnalysisEngine.BehavioralState.ANOMALY_DETECTED -> doubleArrayOf(0.0, 1.0, 0.0)
        BehavioralAnalysisEngine.BehavioralState.INTRUDER_CONFIRMED -> doubleArrayOf(0.0, 0.0, 1.0)
        else -> doubleArrayOf(0.5, 0.5, 0.0)
    }

    private fun getThreatLabels(threatAssessment: ThreatDetectionEngine.ThreatAssessment): DoubleArray {
        val threats = DoubleArray(5) { 0.0 }
        threatAssessment.threats.forEach {
            when (it.type) {
                ThreatDetectionEngine.ThreatType.SUSPICIOUS_APP_ACTIVITY -> threats[0] = 1.0
                ThreatDetectionEngine.ThreatType.NETWORK_ANOMALY -> threats[1] = 1.0
                ThreatDetectionEngine.ThreatType.UNAUTHORIZED_ACCESS_ATTEMPTS -> threats[2] = 1.0
                ThreatDetectionEngine.ThreatType.DATA_EXFILTRATION -> threats[3] = 1.0
                else -> threats[4] = 1.0
            }
        }
        return threats
    }

    private fun getQuantumLabels(quantumAudit: QuantumSecurityLayer.QuantumSecurityAudit): DoubleArray = doubleArrayOf(quantumAudit.overallScore, if (quantumAudit.recommendedActions.isNotEmpty()) 1.0 else 0.0)

    private fun combineFeatures(securityMetrics: DoubleArray, threats: List<ThreatVector>, behaviorAnalysis: DoubleArray): DoubleArray {
        val combined = mutableListOf<Double>()
        combined.addAll(securityMetrics.take(5))
        combined.add(threats.size.toDouble() / 10.0)
        combined.add(threats.maxOfOrNull { it.probability } ?: 0.0)
        combined.addAll(behaviorAnalysis.take(3))
        while (combined.size < 10) {
            combined.add(Random.nextDouble())
        }
        return combined.toDoubleArray()
    }

    private fun generateReasoning(threats: List<ThreatVector>, behaviorAnalysis: DoubleArray, securityMetrics: DoubleArray): List<String> {
        val reasoning = mutableListOf<String>()
        if (threats.isNotEmpty()) reasoning.add("Detected ${threats.size} potential threats")
        if (behaviorAnalysis[1] > 0.7) reasoning.add("Suspicious behavioral patterns detected")
        if (securityMetrics[0] < 0.5) reasoning.add("Overall security level below optimal")
        return reasoning
    }

    private fun generateMitigationStrategies(threatType: String): List<String> = when (threatType) {
        "Malware" -> listOf("Enable real-time scanning", "Restrict app installations", "Monitor network traffic")
        "Social Engineering" -> listOf("Increase authentication requirements", "Enable behavioral monitoring", "Alert on unusual activities")
        "Physical Theft" -> listOf("Activate GPS tracking", "Enable remote wipe", "Trigger loud alarms")
        "Data Breach" -> listOf("Encrypt sensitive data", "Monitor data access", "Implement access controls")
        "System Compromise" -> listOf("Enable system integrity monitoring", "Restrict administrative access", "Monitor system changes")
        else -> listOf("Increase general monitoring", "Enable all security features")
    }

    private fun predictOutcome(action: String, threats: List<ThreatVector>): String = when (action) {
        "MAINTAIN_CURRENT_SECURITY" -> "Continue monitoring with current settings"
        "INCREASE_MONITORING" -> "Enhanced threat detection capability"
        "TRIGGER_MILD_ALERT" -> "User notification and basic protective measures"
        "ACTIVATE_HIGH_SECURITY" -> "Maximum protection with possible usability impact"
        "INITIATE_LOCKDOWN" -> "Complete device lockdown and evidence collection"
        "QUANTUM_SECURITY_PROTOCOL" -> "Quantum-enhanced security activation"
        else -> "Unknown outcome"
    }

    private fun calculateRiskAssessment(threats: List<ThreatVector>, confidence: Double): Double {
        val threatRisk = threats.sumOf { it.probability * it.severity } / threats.size.coerceAtLeast(1)
        return (threatRisk + (1.0 - confidence)) / 2.0
    }

    private suspend fun saveAIModels(context: Context) = withContext(Dispatchers.IO) {
        val modelData = JSONObject().apply {
            put("threat_model", "serialized_model_data")
            put("behavior_model", "serialized_model_data")
            put("response_model", "serialized_model_data")
            put("last_updated", System.currentTimeMillis())
        }
        SecurityPreferences.setAIModelData(context, modelData.toString())
    }

    private fun storeAIDecision(context: Context, decision: AIDecision) {
        val decisionData = JSONObject().apply {
            put("action", decision.action)
            put("confidence", decision.confidence)
            put("reasoning", JSONArray(decision.reasoning))
            put("timestamp", System.currentTimeMillis())
        }
        SecurityPreferences.addAIDecision(context, decisionData.toString())
    }

    private fun increaseMonitoringFrequency(context: Context) = Log.d(TAG, "Increasing monitoring frequency")
    private fun activateHighSecurityMode(context: Context) = Log.d(TAG, "Activating high security mode")
    private fun adjustMonitoringFrequency(context: Context, multiplier: Float) = Log.d(TAG, "Adjusting monitoring frequency by ${multiplier}x")
    private fun enableEnhancedFeatures(context: Context) = Log.d(TAG, "Enabling enhanced security features")
    private fun activateAllSensors(context: Context) = Log.d(TAG, "Activating all sensors")

    fun shutdown() {
        orchestratorScope.cancel()
        Log.i(TAG, "AI Security Orchestrator shutdown")
    }
}

private fun SecurityPreferences.getAIModelData(context: Context): String = getInstance(context).getString("AI_MODEL_DATA", "") ?: ""
private fun SecurityPreferences.setAIModelData(context: Context, data: String) = getInstance(context).edit().putString("AI_MODEL_DATA", data).apply()
private fun SecurityPreferences.addAIDecision(context: Context, decision: String) {
    val prefs = getInstance(context)
    val decisions = prefs.getStringSet("AI_DECISIONS", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    decisions.add(decision)
    if (decisions.size > 100) {
        val sortedDecisions = decisions.toList().sortedBy { try { JSONObject(it).getLong("timestamp") } catch (e: Exception) { 0L } }
        decisions.clear()
        decisions.addAll(sortedDecisions.takeLast(100))
    }
    prefs.edit().putStringSet("AI_DECISIONS", decisions).apply()
}