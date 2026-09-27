package com.dabblepoint.travelagent.service


import com.dabblepoint.travelagent.cache.FlightCache
import com.dabblepoint.travelagent.model.FlightState
import com.dabblepoint.travelagent.model.OpenSkyResponse
import com.dabblepoint.travelagent.model.toFlightState
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class FlightService(
    private val openSkyRestClient: RestClient,
    private val flightCache: FlightCache
) {
    fun flightsInBox(lat: Double, lon: Double, deg: Double = 1.0): List<FlightState> {
        flightCache.get(lat, lon, deg)?.let { return it }
        val response = openSkyRestClient.get()
            .uri("/states/all?lamin={a}&lomin={b}&lamax={c}&lomax={d}",
                lat - deg, lon - deg, lat + deg, lon + deg)
            .retrieve()
            .body(OpenSkyResponse::class.java)

        val states = response?.states
        val flights = if (states == null || !states.isArray) emptyList()
        else (0 until states.size()).mapNotNull { states.get(it).toFlightState() }
        flightCache.put(lat, lon, deg, flights)
        return flights
    }

    fun getFlightSummary(lat: Double, lon: Double): String {
        val flight = flightsInBox(lat, lon).firstOrNull { !it.onGround }
            ?: return "No airborne flights near ($lat, $lon)"
        return "Flight ${flight.callsign} (${flight.originCountry}) " +
                "at ${flight.latitude}, ${flight.longitude} · " +
                "alt ${flight.baroAltitude?.toInt()}m · ${flight.velocity?.toInt()} m/s"
    }
}