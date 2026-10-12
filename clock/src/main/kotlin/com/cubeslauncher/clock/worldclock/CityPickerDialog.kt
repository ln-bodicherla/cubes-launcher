package com.cubeslauncher.clock.worldclock

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.clock.R
import com.cubeslauncher.clock.model.WorldClockCity

class CityPickerDialog(
    private val onCitySelected: (WorldClockCity) -> Unit
) : DialogFragment() {

    companion object {
        val ALL_CITIES = listOf(
            WorldClockCity("Abu Dhabi", "Asia/Dubai"),
            WorldClockCity("Amsterdam", "Europe/Amsterdam"),
            WorldClockCity("Anchorage", "America/Anchorage"),
            WorldClockCity("Athens", "Europe/Athens"),
            WorldClockCity("Auckland", "Pacific/Auckland"),
            WorldClockCity("Bangkok", "Asia/Bangkok"),
            WorldClockCity("Barcelona", "Europe/Madrid"),
            WorldClockCity("Beijing", "Asia/Shanghai"),
            WorldClockCity("Berlin", "Europe/Berlin"),
            WorldClockCity("Bogota", "America/Bogota"),
            WorldClockCity("Brussels", "Europe/Brussels"),
            WorldClockCity("Buenos Aires", "America/Argentina/Buenos_Aires"),
            WorldClockCity("Cairo", "Africa/Cairo"),
            WorldClockCity("Cape Town", "Africa/Johannesburg"),
            WorldClockCity("Chicago", "America/Chicago"),
            WorldClockCity("Copenhagen", "Europe/Copenhagen"),
            WorldClockCity("Dallas", "America/Chicago"),
            WorldClockCity("Delhi", "Asia/Kolkata"),
            WorldClockCity("Denver", "America/Denver"),
            WorldClockCity("Dubai", "Asia/Dubai"),
            WorldClockCity("Dublin", "Europe/Dublin"),
            WorldClockCity("Frankfurt", "Europe/Berlin"),
            WorldClockCity("Helsinki", "Europe/Helsinki"),
            WorldClockCity("Hong Kong", "Asia/Hong_Kong"),
            WorldClockCity("Honolulu", "Pacific/Honolulu"),
            WorldClockCity("Houston", "America/Chicago"),
            WorldClockCity("Istanbul", "Europe/Istanbul"),
            WorldClockCity("Jakarta", "Asia/Jakarta"),
            WorldClockCity("Johannesburg", "Africa/Johannesburg"),
            WorldClockCity("Karachi", "Asia/Karachi"),
            WorldClockCity("Kiev", "Europe/Kiev"),
            WorldClockCity("Kuala Lumpur", "Asia/Kuala_Lumpur"),
            WorldClockCity("Lagos", "Africa/Lagos"),
            WorldClockCity("Lima", "America/Lima"),
            WorldClockCity("Lisbon", "Europe/Lisbon"),
            WorldClockCity("London", "Europe/London"),
            WorldClockCity("Los Angeles", "America/Los_Angeles"),
            WorldClockCity("Madrid", "Europe/Madrid"),
            WorldClockCity("Manila", "Asia/Manila"),
            WorldClockCity("Melbourne", "Australia/Melbourne"),
            WorldClockCity("Mexico City", "America/Mexico_City"),
            WorldClockCity("Miami", "America/New_York"),
            WorldClockCity("Milan", "Europe/Rome"),
            WorldClockCity("Montreal", "America/Montreal"),
            WorldClockCity("Moscow", "Europe/Moscow"),
            WorldClockCity("Mumbai", "Asia/Kolkata"),
            WorldClockCity("Nairobi", "Africa/Nairobi"),
            WorldClockCity("New York", "America/New_York"),
            WorldClockCity("Oslo", "Europe/Oslo"),
            WorldClockCity("Paris", "Europe/Paris"),
            WorldClockCity("Perth", "Australia/Perth"),
            WorldClockCity("Prague", "Europe/Prague"),
            WorldClockCity("Riyadh", "Asia/Riyadh"),
            WorldClockCity("Rome", "Europe/Rome"),
            WorldClockCity("San Francisco", "America/Los_Angeles"),
            WorldClockCity("Santiago", "America/Santiago"),
            WorldClockCity("Sao Paulo", "America/Sao_Paulo"),
            WorldClockCity("Seattle", "America/Los_Angeles"),
            WorldClockCity("Seoul", "Asia/Seoul"),
            WorldClockCity("Shanghai", "Asia/Shanghai"),
            WorldClockCity("Singapore", "Asia/Singapore"),
            WorldClockCity("Stockholm", "Europe/Stockholm"),
            WorldClockCity("Sydney", "Australia/Sydney"),
            WorldClockCity("Taipei", "Asia/Taipei"),
            WorldClockCity("Tehran", "Asia/Tehran"),
            WorldClockCity("Tel Aviv", "Asia/Jerusalem"),
            WorldClockCity("Tokyo", "Asia/Tokyo"),
            WorldClockCity("Toronto", "America/Toronto"),
            WorldClockCity("Vancouver", "America/Vancouver"),
            WorldClockCity("Vienna", "Europe/Vienna"),
            WorldClockCity("Warsaw", "Europe/Warsaw"),
            WorldClockCity("Washington D.C.", "America/New_York"),
            WorldClockCity("Zurich", "Europe/Zurich")
        )
    }

    private var filteredCities = ALL_CITIES.toMutableList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.Theme_CubesLauncher)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_city_picker, container, false)

        val searchEdit = view.findViewById<EditText>(R.id.searchEdit)
        val recyclerView = view.findViewById<RecyclerView>(R.id.citiesRecycler)

        val adapter = CityListAdapter(filteredCities) { city ->
            onCitySelected(city)
            dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.lowercase() ?: ""
                filteredCities.clear()
                if (query.isEmpty()) {
                    filteredCities.addAll(ALL_CITIES)
                } else {
                    filteredCities.addAll(ALL_CITIES.filter {
                        it.cityName.lowercase().contains(query)
                    })
                }
                adapter.notifyDataSetChanged()
            }
        })

        return view
    }

    private class CityListAdapter(
        private val cities: List<WorldClockCity>,
        private val onCityClick: (WorldClockCity) -> Unit
    ) : RecyclerView.Adapter<CityListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cityName: TextView = view.findViewById(R.id.pickerCityName)
            val cityTimezone: TextView = view.findViewById(R.id.pickerCityTimezone)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_city_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val city = cities[position]
            holder.cityName.text = city.cityName
            holder.cityTimezone.text = city.timeZoneId
            holder.itemView.setOnClickListener { onCityClick(city) }
        }

        override fun getItemCount(): Int = cities.size
    }
}
