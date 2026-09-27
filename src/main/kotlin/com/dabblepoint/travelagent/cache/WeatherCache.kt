package com.dabblepoint.travelagent.cache
import com.dabblepoint.travelagent.model.CurrentWeather
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.Locale

@Component
class WeatherCache(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    private val ttl = Duration.ofMinutes(10)


    fun get(lat: Double, long: Double): CurrentWeather? =
        redis.opsForValue().get(key(lat, long))?.let {
            objectMapper.readValue(it, CurrentWeather::class.java)
        }

    fun put(lat: Double, lon: Double, weather: CurrentWeather) {
        redis.opsForValue().set(key(lat, lon), objectMapper.writeValueAsString(weather), ttl)
    }

    private fun key(lat: Double, lon: Double) =
        "weather:%.4f:%.4f".format(Locale.ROOT, lat, lon)
}