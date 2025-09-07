package com.hamoon.uncleted.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentRemoteBinding
import com.hamoon.uncleted.util.EmailSender
import com.hamoon.uncleted.util.RootChecker
import com.hamoon.uncleted.util.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteFragment : Fragment() {

    private var _binding: FragmentRemoteBinding? = null
    private val binding get() = _binding!!
    private var isRooted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            isRooted = RootChecker.isDeviceRooted()
            binding.layoutRemoteInstallCode.isVisible = isRooted
            loadSettings()
            setupListeners()
        }
    }

    private fun setupListeners() {
        binding.btnSaveRemote.setOnClickListener {
            saveSettings()
            Toast.makeText(requireContext(), "Remote settings saved!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSetEmailCredentials.setOnClickListener { showEmailCredentialsDialog() }
        binding.btnSendTestEmail.setOnClickListener { sendTestEmail() }
    }

    private fun loadSettings() {
        binding.etEmergencyContact.setText(SecurityPreferences.getEmergencyContact(requireContext()))
        // ### MODIFIED: Load the new master password ###
        binding.etSmsMasterPassword.setText(SecurityPreferences.getSmsMasterPassword(requireContext()))

        if (isRooted) {
            binding.etRemoteInstallCode.setText(SecurityPreferences.getRemoteInstallCode(requireContext()))
        }
    }

    private fun saveSettings() {
        SecurityPreferences.setEmergencyContact(requireContext(), binding.etEmergencyContact.text.toString())
        // ### MODIFIED: Save the new master password ###
        SecurityPreferences.setSmsMasterPassword(requireContext(), binding.etSmsMasterPassword.text.toString())

        if (isRooted) {
            SecurityPreferences.setRemoteInstallCode(requireContext(), binding.etRemoteInstallCode.text.toString())
        }
    }

    private fun showEmailCredentialsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_email_credentials, null)
        val etHost = dialogView.findViewById<TextInputEditText>(R.id.et_email_host_dialog)
        val etPort = dialogView.findViewById<TextInputEditText>(R.id.et_email_port_dialog)
        val etUsername = dialogView.findViewById<TextInputEditText>(R.id.et_email_username_dialog)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.et_email_password_dialog)
        val switchSslTls = dialogView.findViewById<SwitchMaterial>(R.id.switch_enable_ssl_tls_dialog)

        val currentConfig = EmailSender.getEmailConfig(requireContext())
        etHost.setText(currentConfig?.host)
        etPort.setText(currentConfig?.port?.toString())
        etUsername.setText(currentConfig?.username)
        etPassword.setText(currentConfig?.password)
        switchSslTls.isChecked = currentConfig?.enableSslTls ?: true

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.set_email_credentials_title)
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val host = etHost.text.toString()
                val port = etPort.text.toString().toIntOrNull()
                val username = etUsername.text.toString()
                val password = etPassword.text.toString()
                val enableSslTls = switchSslTls.isChecked

                if (host.isNotEmpty() && port != null && username.isNotEmpty() && password.isNotEmpty()) {
                    val config = EmailSender.EmailConfig(host, port, username, password, enableSslTls)
                    EmailSender.setEmailConfig(requireContext(), config)
                    Toast.makeText(requireContext(), "Email credentials saved.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.email_credentials_missing, Toast.LENGTH_LONG).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendTestEmail() {
        val recipient = SecurityPreferences.getEmergencyContact(requireContext())
        if (recipient.isNullOrEmpty() || !recipient.contains("@")) {
            Toast.makeText(requireContext(), "Set a valid email in 'Emergency Contact' first.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(requireContext(), "Sending test email...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val success = EmailSender.sendEmail(
                requireContext(),
                recipient,
                "Uncle Ted Test Email",
                "This is a test email from your Uncle Ted app."
            )
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(requireContext(), getString(R.string.email_sending_success), Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = getString(R.string.email_sending_failed, "Check logs for details.")
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}