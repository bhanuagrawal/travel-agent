package com.dabblepoint.travelagent.cache

import com.dabblepoint.travelagent.model.FlightState
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.Locale

@Component
class FlightCache(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val ttl = Duration.ofSeconds(30)

    fun get(lat: Double, lon: Double, deg: Double): List<FlightState>? {
        val json = redis.opsForValue().get(key(lat, lon, deg)) ?: return null
        return objectMapper.readValue(json, Array<FlightState>::class.java).toList()
    }

    fun put(lat: Double, lon: Double, deg: Double, flights: List<FlightState>) {
        redis.opsForValue().set(key(lat, lon, deg), objectMapper.writeValueAsString(flights), ttl)
    }

    private fun key(lat: Double, lon: Double, deg: Double) =
        "flights:%.4f:%.4f:%.2f".format(Locale.ROOT, lat, lon, deg)
}