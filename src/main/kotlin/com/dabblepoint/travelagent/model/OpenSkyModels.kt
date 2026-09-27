package com.dabblepoint.travelagent.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenSkyResponse(
    val time: Long = 0,
    val states: JsonNode? = null
)

data class FlightState(
    val icao24: String,
    val callsign: String,
    val originCountry: String,
    val longitude: Double?,
    val latitude: Double?,
    val baroAltitude: Double?,
    val onGround: Boolean,
    val velocity: Double?,
    val trueTrack: Double?
)

fun JsonNode.toFlightState(): FlightState? {
    if (!isArray || size() < 11) return null
    fun str(i: Int): String? = get(i)?.takeIf { it.isString }?.asString()
    fun dbl(i: Int): Double? = get(i)?.takeIf { it.isNumber }?.asDouble()
    fun bool(i: Int): Boolean = get(i)?.takeIf { it.isBoolean }?.asBoolean() ?: false

    val icao24 = str(0) ?: return null
    return FlightState(
        icao24 = icao24,
        callsign = str(1)?.trim().orEmpty(),
        originCountry = str(2) ?: "",
        longitude = dbl(5),
        latitude = dbl(6),
        baroAltitude = dbl(7),
        onGround = bool(8),
        velocity = dbl(9),
        trueTrack = dbl(10)
    )
}