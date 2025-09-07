package com.hamoon.uncleted

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.ActivityMainBinding
import com.hamoon.uncleted.fragments.*
import com.hamoon.uncleted.services.MonitoringService
import com.hamoon.uncleted.util.BiometricAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle

    private val dashboardFragment by lazy { DashboardFragment() }
    private val permissionsFragment by lazy { PermissionsFragment() }
    private val pinsFragment by lazy { PinsFragment() }
    private val remoteFragment by lazy { RemoteFragment() }
    private val featuresFragment by lazy { FeaturesFragment() }
    private val settingsFragment by lazy { SettingsFragment() }
    private val aboutFragment by lazy { AboutFragment() }
    private var activeFragment: Fragment = dashboardFragment
    private var isAuthenticating = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ### START: PRECISE FIX FOR INITIAL HICCUP ###
        // The original code performed a slow, blocking check for SecurityPreferences here.
        // This was a primary cause of the "skipped frames" error.
        // The new logic immediately shows a loading indicator and defers the slow work
        // to a background thread, ensuring the UI is responsive from the very start.

        // 1. Immediately hide the main UI and show the loading indicator.
        binding.drawerLayout.visibility = View.INVISIBLE
        binding.initialLoadingIndicator.visibility = View.VISIBLE

        // 2. Launch a coroutine to do the slow work in the background.
        lifecycleScope.launch {
            val requiresAuth = withContext(Dispatchers.IO) {
                // This is the slow part: initializing EncryptedSharedPreferences
                val isBiometricLockEnabled = SecurityPreferences.isBiometricLockEnabled(this@MainActivity)
                isBiometricLockEnabled && BiometricAuthManager.isBiometricAvailable(this@MainActivity)
            }

            // 3. Back on the main thread, decide the next step.
            if (requiresAuth) {
                // The loading indicator is already showing, so just prompt for auth.
                promptBiometricAuth()
            } else {
                // No auth needed, proceed directly to showing the UI.
                isAuthenticating = false
                binding.initialLoadingIndicator.visibility = View.GONE
                binding.drawerLayout.visibility = View.VISIBLE
                initializeUi()
            }
        }

        // 4. Ensure the core monitoring service is running (this is a fast operation).
        startMonitoringServiceIfNeeded()
        // ### END: PRECISE FIX FOR INITIAL HICCUP ###
    }

    private fun promptBiometricAuth() {
        BiometricAuthManager.authenticateUser(this,
            title = "Uncle Ted Security",
            subtitle = "Authenticate to access the application",
            callback = object : BiometricAuthManager.AuthCallback {
                override fun onAuthResult(result: BiometricAuthManager.AuthResult, errorMessage: String?) {
                    when (result) {
                        BiometricAuthManager.AuthResult.SUCCESS -> {
                            isAuthenticating = false
                            // On success, hide loading and show the UI.
                            binding.initialLoadingIndicator.visibility = View.GONE
                            binding.drawerLayout.visibility = View.VISIBLE
                            initializeUi()
                        }
                        else -> {
                            Toast.makeText(this@MainActivity, "Authentication failed. Exiting.", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            })
    }

    private fun initializeUi() {
        setSupportActionBar(binding.toolbar)
        toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)

        setupFragments()
        showFragment(dashboardFragment, getString(R.string.menu_dashboard))
        binding.navView.setCheckedItem(R.id.nav_dashboard)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isAuthenticating) return

                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else if (activeFragment !is DashboardFragment) {
                    showFragment(dashboardFragment, getString(R.string.menu_dashboard))
                    binding.navView.setCheckedItem(R.id.nav_dashboard)
                } else {
                    finish()
                }
            }
        })
    }

    private fun startMonitoringServiceIfNeeded() {
        val serviceIntent = Intent(this, MonitoringService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Log.i("MainActivity", "Ensured MonitoringService is started.")
    }

    private fun setupFragments() {
        supportFragmentManager.commit {
            add(R.id.nav_host_fragment, dashboardFragment, "DASHBOARD").hide(dashboardFragment)
            add(R.id.nav_host_fragment, permissionsFragment, "PERMISSIONS").hide(permissionsFragment)
            add(R.id.nav_host_fragment, pinsFragment, "PINS").hide(pinsFragment)
            add(R.id.nav_host_fragment, remoteFragment, "REMOTE").hide(remoteFragment)
            add(R.id.nav_host_fragment, featuresFragment, "FEATURES").hide(featuresFragment)
            add(R.id.nav_host_fragment, settingsFragment, "SETTINGS").hide(settingsFragment)
            add(R.id.nav_host_fragment, aboutFragment, "ABOUT").hide(aboutFragment)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val (fragment, title) = when (item.itemId) {
            R.id.nav_dashboard -> dashboardFragment to getString(R.string.menu_dashboard)
            R.id.nav_permissions -> permissionsFragment to getString(R.string.menu_permissions)
            R.id.nav_pins -> pinsFragment to getString(R.string.menu_pins)
            R.id.nav_remote -> remoteFragment to getString(R.string.menu_remote)
            R.id.nav_features -> featuresFragment to getString(R.string.menu_features)
            R.id.nav_settings -> settingsFragment to getString(R.string.settings_title)
            R.id.nav_about -> aboutFragment to getString(R.string.menu_about)
            else -> return false
        }
        showFragment(fragment, title)
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showFragment(fragment: Fragment, title: String) {
        if (fragment.isVisible) return

        supportFragmentManager.commit {
            hide(activeFragment)
            show(fragment)
        }
        activeFragment = fragment
        binding.toolbar.title = title
    }
}