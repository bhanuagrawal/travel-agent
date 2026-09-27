package com.dabblepoint.travelagent.model


data class TravelAdvice(
    val location: String,
    val weather: String,
    val nearestFlight: String?,
    val advice: String
)