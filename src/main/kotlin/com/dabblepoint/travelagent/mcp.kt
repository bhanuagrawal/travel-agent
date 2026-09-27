package com.dabblepoint.travelagent

import com.dabblepoint.travelagent.service.FlightService
import com.dabblepoint.travelagent.service.WeatherService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class TravelMcpTools(
    private val weatherService: WeatherService,
    private val flightService: FlightService
) {
    @McpTool(name = "getWeather", description = "Get current weather for a latitude/longitude")
    fun getWeather(
        @McpToolParam(description = "Latitude", required = true) lat: Double,
        @McpToolParam(description = "Longitude", required = true) lon: Double
    ): String = weatherService.getWeatherSummary(lat, lon)

    @McpTool(name = "getFlight", description = "Get the nearest airborne flight for a latitude/longitude")
    fun getFlight(
        @McpToolParam(description = "Latitude", required = true) lat: Double,
        @McpToolParam(description = "Longitude", required = true) lon: Double
    ): String = flightService.getFlightSummary(lat, lon)
}