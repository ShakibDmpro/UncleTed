package com.hamoon.uncleted.fragments

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.LocaleManager

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // Theme Preference Handler
        val themePreference: ListPreference? = findPreference("theme")
        themePreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                // The new value is saved automatically. We just need to apply it.
                applyTheme(newValue as String)
                true
            }

        // ### NEW: Language Preference Handler ###
        val languagePreference: ListPreference? = findPreference("language")
        languagePreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                // Apply the new locale. AppCompat will handle recreating the activity.
                LocaleManager.setLocale(newValue as String)
                true
            }

        // Shake Sensitivity Preference Handler
        val shakePreference: SeekBarPreference? = findPreference("shake_sensitivity")
        shakePreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                SecurityPreferences.setShakeSensitivity(requireContext(), newValue as Int)
                true
            }
    }

    private fun applyTheme(themeValue: String) {
        // This sets the base night mode. The Application class handles applying the specific AMOLED theme.
        val mode = when (themeValue) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark", "amoled" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}