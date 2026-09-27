package com.dabblepoint.travelagent.exception

class WeatherApiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)