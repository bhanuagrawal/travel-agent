package com.dabblepoint.travelagent.controller

import com.dabblepoint.travelagent.agent.TravelTools
import com.dabblepoint.travelagent.kafka.FlightEventProducer
import com.dabblepoint.travelagent.model.AskRequest
import com.dabblepoint.travelagent.model.TravelAdvice
import com.dabblepoint.travelagent.service.FlightService
import com.dabblepoint.travelagent.service.TravelOrchestratorService
import com.dabblepoint.travelagent.service.WeatherService
import jakarta.validation.Valid
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class FlightAgentController(
    private val weatherService: WeatherService,
    private val flightService: FlightService,
    private val orchestrator: TravelOrchestratorService,
    private val chatClient: ChatClient,
    private val travelTools: TravelTools,
    private val lightEventProducer: FlightEventProducer
) {

    @GetMapping("/api/v1/health")
    fun healthCheck(): String{
        return "OK"
    }

    @GetMapping("/api/v1/weather/{lat}/{long}")
    fun weather(@PathVariable lat: Double, @PathVariable long: Double): String = weatherService.getWeatherSummary(lat, long)

    @GetMapping("/api/v1/flights/{lat}/{long}")
    fun flights(
        @PathVariable lat: Double,
        @PathVariable long: Double,
        authentication: Authentication
    ): String {
        val userId = authentication.name
        lightEventProducer.publishFlightSearched(userId, "DEL", "BOM")
        return flightService.getFlightSummary(lat, long)
    }


    @GetMapping("/api/v1/advice/{lat}/{long}")
    fun advice(@PathVariable lat: Double, @PathVariable long: Double, @RequestParam(required = false, defaultValue = "Delhi") city: String): TravelAdvice =
        orchestrator.advise(lat, long, city)

    @GetMapping("/api/v1/agent/ping")
    fun ping(@RequestParam q: String): String =
        chatClient.prompt()
                .user(q)
                .advisors { it.param(ChatMemory.CONVERSATION_ID, "ping") }
                .call().content() ?: "(no response)"

    @PostMapping("/api/v1/agent/ask")
    @PreAuthorize("hasRole('ADMIN')")
    fun ask(@Valid @RequestBody req: AskRequest): String =
        chatClient.prompt()
            .user(req.question)
            .tools(travelTools)
            .advisors { it.param(ChatMemory.CONVERSATION_ID, req.conversationId) }
            .call()
            .content() ?: "(no response)"
}