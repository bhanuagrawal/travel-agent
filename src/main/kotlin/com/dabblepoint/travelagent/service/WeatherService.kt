package com.dabblepoint.travelagent.service

import com.dabblepoint.travelagent.cache.WeatherCache
import com.dabblepoint.travelagent.exception.WeatherApiException
import com.dabblepoint.travelagent.model.CurrentWeather
import com.dabblepoint.travelagent.model.WeatherResponse
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Service
class WeatherService(
    private val openMeteoRestClient: RestClient,
    private val weatherCache: WeatherCache
) {
    fun getCurrentWeather(lat: Double, lon: Double): CurrentWeather {
        weatherCache.get(lat, lon)?.let { return it }
        val fresh = fetchFromApi(lat, lon)
        weatherCache.put(lat, lon, fresh)
        return fresh
    }


    private fun fetchFromApi(lat: Double, lon: Double): CurrentWeather =
        try {
            openMeteoRestClient.get()
                .uri("/forecast?latitude={lat}&longitude={lon}&current_weather=true", lat, lon)
                .retrieve()
                .body(WeatherResponse::class.java)?.currentWeather
                ?: throw WeatherApiException("Weather provider returned an empty response")
        } catch (e: RestClientResponseException) {
            throw WeatherApiException("Weather provider error: ${e.statusCode} ${e.statusText}", e)
        } catch (e: Exception) {
            throw WeatherApiException("Weather request failed: ${e.message}", e)
        }

    fun getWeatherSummary(lat: Double, lon: Double): String {
        val w = getCurrentWeather(lat, lon) ?: return "Weather data unavailable"
        return "Temp: ${w.temperature}°C, Wind: ${w.windspeed} km/h"
    }
}