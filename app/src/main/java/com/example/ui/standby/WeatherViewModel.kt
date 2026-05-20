package com.example.ui.standby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.CurrentWeather
import com.example.api.WeatherApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WeatherViewModel : ViewModel() {
    private val api = WeatherApi.create()

    private val _weather = MutableStateFlow<CurrentWeather?>(null)
    val weather: StateFlow<CurrentWeather?> = _weather

    init {
        fetchWeather()
    }

    private fun fetchWeather() {
        android.util.Log.d("WeatherViewModel", "fetchWeather started: sending query for San Francisco (37.7749, -122.4194)")
        viewModelScope.launch {
            try {
                // Fetching weather for a default location (e.g. San Francisco) for standby mode
                // In a production app, Location Services would supply lat/lon.
                val result = api.getCurrentWeather(37.7749, -122.4194)
                _weather.value = result.current
                android.util.Log.i("WeatherViewModel", "fetchWeather success: Temp = ${result.current.temperature_2m}°C, Code = ${result.current.weather_code}")
            } catch (e: Exception) {
                android.util.Log.e("WeatherViewModel", "fetchWeather failed: uncaught exception / network failure", e)
                _weather.value = null
            }
        }
    }
}
