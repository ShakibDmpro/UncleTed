package com.hamoon.uncleted.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentPinsBinding

class PinsFragment : Fragment() {

    private var _binding: FragmentPinsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPinsBinding.inflate(inflater, container, false)

        loadSettings()

        binding.btnSavePins.setOnClickListener {
            saveSettings()
            Toast.makeText(requireContext(), "PIN settings saved!", Toast.LENGTH_SHORT).show()
        }

        return binding.root
    }

    private fun loadSettings() {
        binding.etNormalPin.setText(SecurityPreferences.getNormalPin(requireContext()))
        binding.etDuressPin.setText(SecurityPreferences.getDuressPin(requireContext()))
        // ### NEW: Load the Wipe PIN ###
        binding.etWipePin.setText(SecurityPreferences.getWipePin(requireContext()))
    }

    private fun saveSettings() {
        SecurityPreferences.setNormalPin(requireContext(), binding.etNormalPin.text.toString())
        SecurityPreferences.setDuressPin(requireContext(), binding.etDuressPin.text.toString())
        // ### NEW: Save the Wipe PIN ###
        SecurityPreferences.setWipePin(requireContext(), binding.etWipePin.text.toString())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}