package com.cubeslauncher.clock.worldclock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.clock.ClockPreferencesManager
import com.cubeslauncher.clock.databinding.FragmentWorldClockBinding
import com.cubeslauncher.clock.model.WorldClockCity
import com.cubeslauncher.common.theme.AccentColorManager
import kotlinx.coroutines.launch

class WorldClockFragment : Fragment() {

    private var _binding: FragmentWorldClockBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: ClockPreferencesManager
    private val cities = mutableListOf<WorldClockCity>()
    private lateinit var adapter: WorldClockAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            adapter.notifyDataSetChanged()
            handler.postDelayed(this, 60_000L) // Update every minute
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorldClockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = ClockPreferencesManager(requireContext())

        adapter = WorldClockAdapter(cities) { city ->
            // Long click to remove
            cities.remove(city)
            saveCities()
        }

        binding.worldClockRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.worldClockRecycler.adapter = adapter

        binding.addCityButton.setOnClickListener { showCityPicker() }

        val accent = AccentColorManager.getAccentColor()
        binding.addCityButton.setBackgroundColor(accent)

        loadCities()
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateRunnable)
        _binding = null
    }

    private fun loadCities() {
        lifecycleScope.launch {
            cities.clear()
            cities.addAll(prefs.getCities())
            adapter.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    private fun showCityPicker() {
        val dialog = CityPickerDialog { city ->
            // Avoid duplicates
            if (cities.none { it.timeZoneId == city.timeZoneId }) {
                cities.add(city)
                saveCities()
            }
        }
        dialog.show(parentFragmentManager, "city_picker")
    }

    private fun saveCities() {
        lifecycleScope.launch {
            prefs.setCities(cities)
            adapter.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        if (cities.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.worldClockRecycler.visibility = View.GONE
        } else {
            binding.emptyText.visibility = View.GONE
            binding.worldClockRecycler.visibility = View.VISIBLE
        }
    }
}
