package com.hamoon.uncleted.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.hamoon.uncleted.R
import com.hamoon.uncleted.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGithub.setOnClickListener {
            openUrl("https://github.com/HamoonSoleimani")
        }

        binding.btnWebsite.setOnClickListener {
            openUrl("https://hamoon.net/")
        }

        // ### NEW: Logic to display a random quote ###
        displayRandomQuote()
    }

    private fun displayRandomQuote() {
        // Get the array of quotes from resources
        val quotes = resources.getStringArray(R.array.kaczynski_quotes)

        // Check if the array is not empty to avoid a crash
        if (quotes.isNotEmpty()) {
            // Select a random quote
            val randomQuote = quotes.random()
            // Set the text of the TextView
            binding.tvRandomQuote.text = randomQuote
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}