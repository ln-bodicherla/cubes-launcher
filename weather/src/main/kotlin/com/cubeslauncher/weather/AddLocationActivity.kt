package com.cubeslauncher.weather

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper
import com.cubeslauncher.weather.api.WeatherApiClient
import com.cubeslauncher.weather.api.WeatherParser
import com.cubeslauncher.weather.databinding.ActivityAddLocationBinding
import com.cubeslauncher.weather.databinding.ItemLocationSearchBinding
import com.cubeslauncher.weather.model.SavedLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AddLocationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddLocationBinding
    private lateinit var prefs: WeatherPreferencesManager
    private lateinit var searchAdapter: LocationSearchAdapter

    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        AccentColorManager.init(this)

        binding = ActivityAddLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs = WeatherPreferencesManager(applicationContext)

        setupUI()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }

        searchAdapter = LocationSearchAdapter { location ->
            onLocationSelected(location)
        }

        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AddLocationActivity)
            adapter = searchAdapter
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    debounceSearch(query)
                } else {
                    searchAdapter.submitList(emptyList())
                    binding.searchResultsRecyclerView.visibility = View.GONE
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.emptyStateText.text = getString(R.string.search_empty_state)
                }
            }
        })

        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchEditText.text?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun debounceSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(400)
            performSearch(query)
        }
    }

    private fun performSearch(query: String) {
        binding.searchLoadingIndicator.visibility = View.VISIBLE
        binding.emptyStateText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val rawJson = WeatherApiClient.searchCities(query)
                val results = WeatherParser.parseGeocodingResults(rawJson)

                binding.searchLoadingIndicator.visibility = View.GONE

                if (results.isEmpty()) {
                    searchAdapter.submitList(emptyList())
                    binding.searchResultsRecyclerView.visibility = View.GONE
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.emptyStateText.text = getString(R.string.no_results)
                } else {
                    searchAdapter.submitList(results)
                    binding.searchResultsRecyclerView.visibility = View.VISIBLE
                    binding.emptyStateText.visibility = View.GONE
                }
            } catch (e: Exception) {
                binding.searchLoadingIndicator.visibility = View.GONE
                binding.emptyStateText.visibility = View.VISIBLE
                binding.emptyStateText.text = getString(R.string.error_searching)
            }
        }
    }

    private fun onLocationSelected(location: SavedLocation) {
        lifecycleScope.launch {
            prefs.addLocation(location)
            // Set the newly added location as the selected one
            val locations = prefs.savedLocations.first()
            val newIndex = locations.indexOfFirst {
                it.latitude == location.latitude && it.longitude == location.longitude
            }
            if (newIndex >= 0) {
                prefs.setSelectedLocationIndex(newIndex)
            }
            finish()
        }
    }

    // --- Inner adapter class ---
    private class LocationSearchAdapter(
        private val onItemClick: (SavedLocation) -> Unit
    ) : RecyclerView.Adapter<LocationSearchAdapter.ViewHolder>() {

        private var items: List<SavedLocation> = emptyList()

        fun submitList(list: List<SavedLocation>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLocationSearchBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(
            private val binding: ItemLocationSearchBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(location: SavedLocation) {
                binding.cityNameText.text = location.name

                val detail = buildString {
                    if (location.admin.isNotEmpty()) {
                        append(location.admin)
                    }
                    if (location.country.isNotEmpty()) {
                        if (isNotEmpty()) append(", ")
                        append(location.country)
                    }
                }
                binding.locationDetailText.text = detail

                binding.root.setOnClickListener {
                    onItemClick(location)
                }
            }
        }
    }
}
