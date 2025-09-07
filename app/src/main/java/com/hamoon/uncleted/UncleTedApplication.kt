package com.hamoon.uncleted

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.hamoon.uncleted.util.AppLifecycleManager
import com.hamoon.uncleted.util.LocaleManager

class UncleTedApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ### NEW: Apply the selected language at the very beginning ###
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val languageValue = sharedPreferences.getString("language", "system") ?: "system"
        LocaleManager.setLocale(languageValue)
        // ### END NEW ###


        // ### START: PRECISE FIX FOR CONSTANT ANALYSIS ###
        // Register a global lifecycle callback to track when the app moves to the
        // foreground or background. This state is used by the AI and Quantum
        // security layers to pause their intensive analysis loops when the app is not visible.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                AppLifecycleManager.onActivityStarted()
            }

            override fun onActivityStopped(activity: Activity) {
                AppLifecycleManager.onActivityStopped()
            }
            // ### END: PRECISE FIX FOR CONSTANT ANALYSIS ###


            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@UncleTedApplication)
                val themeValue = sharedPreferences.getString("theme", "system")
                applyNightModeForActivity(themeValue)
                if (themeValue == "amoled") {
                    when (activity) {
                        is MainActivity, is LockScreenActivity, is FakeShutdownActivity -> {
                            activity.setTheme(R.style.Theme_UncleTed_Amoled)
                        }
                        is CameraPermissionBrokerActivity -> {
                            activity.setTheme(R.style.Theme_UncleTed_Amoled_Transparent)
                        }
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun applyNightModeForActivity(themeValue: String?) {
        when (themeValue) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark", "amoled" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}