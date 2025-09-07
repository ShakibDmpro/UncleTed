package com.hamoon.uncleted.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hamoon.uncleted.FakeShutdownActivity
import com.hamoon.uncleted.data.SecurityPreferences

/**
 * An advanced Accessibility Service that detects the system power menu by inspecting its content.
 * This approach is more robust than relying on specific class names, which vary across Android
 * versions and device manufacturers. When a power menu is detected, it is immediately covered by
 * our FakeShutdownActivity to prevent the device from being turned off.
 */
class PowerButtonService : AccessibilityService() {

    private val TAG = "PowerButtonService"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only care about when the window configuration changes, as this is when a dialog appears.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return

            // Recursively search the new window's view hierarchy for power-related keywords.
            if (isPowerMenu(rootNode)) {
                Log.i(TAG, "Detected system power menu by its content.")

                if (SecurityPreferences.isFakeShutdownEnabled(this)) {
                    Log.i(TAG, "Fake shutdown enabled, launching FakeShutdownActivity.")
                    // Launch our fake screen over the real one.
                    val intent = Intent(this, FakeShutdownActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)

                    // After launching our screen, attempt to programmatically press the "back" button
                    // to dismiss the real power menu that is now hidden behind our fake one.
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } else {
                    Log.d(TAG, "Fake shutdown is disabled in settings; ignoring power menu.")
                }
            }
        }
    }

    /**
     * Recursively traverses an AccessibilityNodeInfo tree to find text content that indicates
     * a power menu.
     *
     * @param node The root node of the view hierarchy to search.
     * @return `true` if power-related keywords are found, `false` otherwise.
     */
    private fun isPowerMenu(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // A list of common, case-insensitive keywords found in power menus across various devices.
        val powerKeywords = listOf("power off", "restart", "reboot", "emergency mode", "shutdown")

        // Check the node's own text or content description.
        val nodeText = (node.text?.toString() ?: node.contentDescription?.toString())?.lowercase()
        if (nodeText != null && powerKeywords.any { keyword -> nodeText.contains(keyword) }) {
            return true
        }

        // If not found in the current node, search its children recursively.
        for (i in 0 until node.childCount) {
            if (isPowerMenu(node.getChild(i))) {
                return true
            }
        }

        return false
    }


    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected.")
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.")
    }

    override fun onDestroy() {
        Log.d(TAG, "Accessibility service destroyed.")
        super.onDestroy()
    }
}