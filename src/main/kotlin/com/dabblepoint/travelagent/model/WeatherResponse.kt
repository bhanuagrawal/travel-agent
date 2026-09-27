package com.dabblepoint.travelagent.model

import com.fasterxml.jackson.annotation.JsonProperty

data class WeatherResponse(
    @JsonProperty("current_weather")
    val currentWeather: CurrentWeather? = null
)

data class CurrentWeather(
    val temperature: Double = 0.0,
    val windspeed: Double = 0.0,
    val weathercode: Int = 0
)