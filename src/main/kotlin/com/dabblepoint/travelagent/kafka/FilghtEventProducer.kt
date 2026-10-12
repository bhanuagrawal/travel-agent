package com.dabblepoint.travelagent.kafka

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service
class FlightEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    private val objectMapper = ObjectMapper()

    fun publishFlightSearched(userId: String, origin: String, destination: String) {
        val event = mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "userId" to userId,
            "origin" to origin,
            "destination" to destination,
            "searchTimestamp" to java.time.Instant.now().toString()
        )
        val json = objectMapper.writeValueAsString(event)
        kafkaTemplate.send("flight-searched", userId, json)
    }
}