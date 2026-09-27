package com.dabblepoint.travelagent.service


import com.dabblepoint.travelagent.model.FlightState
import com.dabblepoint.travelagent.model.TravelAdvice
import org.springframework.stereotype.Service

@Service
class TravelOrchestratorService(
    private val weatherService: WeatherService,
    private val flightService: FlightService
) {
    fun advise(lat: Double, lon: Double, label: String): TravelAdvice {
        val weather = weatherService.getCurrentWeather(lat, lon)
        val flight = flightService.flightsInBox(lat, lon).firstOrNull { !it.onGround }

        return TravelAdvice(
            location = label,
            weather = weather?.let { "${describe(it.weathercode)}, ${it.temperature}°C" } ?: "unavailable",
            nearestFlight = flight?.let { "${it.callsign} (${it.originCountry})" },
            advice = buildAdvice(weather?.weathercode, flight)
        )
    }

    private fun buildAdvice(code: Int?, flight: FlightState?): String = when {
        code == null -> "No weather data — check manually before travel."
        code >= 95 -> "Thunderstorms — expect delays. If delayed >3h (EU261), compensation may apply."
        code >= 51 -> "Rain/low visibility — possible delays. Keep rebooking options ready."
        flight != null -> "Clear conditions. ${flight.callsign} airborne normally."
        else -> "Clear conditions. No airborne traffic nearby."
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "Clear sky"
        in 1..3 -> "Partly cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Rain showers"
        in 95..99 -> "Thunderstorm"
        else -> "Unknown"
    }
}