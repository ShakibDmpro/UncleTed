package com.hamoon.uncleted.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A simple, manually-controlled LifecycleOwner for use in background services
 * where a traditional UI lifecycle is not available. This is crucial for
 * one-off operations with CameraX in a service.
 */
class FakeLifecycleOwner : LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    init {
        // Start in the CREATED state.
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    /**
     * Moves the lifecycle to the STARTED state, allowing CameraX to bind.
     */
    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    /**
     * Moves the lifecycle to the DESTROYED state, ensuring CameraX resources are released.
     */
    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}