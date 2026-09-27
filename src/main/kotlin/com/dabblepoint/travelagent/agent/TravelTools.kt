package com.dabblepoint.travelagent.agent

import com.dabblepoint.travelagent.service.FlightService
import com.dabblepoint.travelagent.service.WeatherService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class TravelTools(
    private val weatherService: WeatherService,
    private val flightService: FlightService
) {
    @Tool(description = "Get the current weather for a location given latitude and longitude.")
    fun getWeather(
        @ToolParam(description = "Latitude in decimal degrees") lat: Double,
        @ToolParam(description = "Longitude in decimal degrees") lon: Double
    ): String = weatherService.getWeatherSummary(lat, lon)

    @Tool(description = "Get the nearest airborne flight for a location given latitude and longitude.")
    fun getFlight(
        @ToolParam(description = "Latitude in decimal degrees") lat: Double,
        @ToolParam(description = "Longitude in decimal degrees") lon: Double
    ): String = flightService.getFlightSummary(lat, lon)
}