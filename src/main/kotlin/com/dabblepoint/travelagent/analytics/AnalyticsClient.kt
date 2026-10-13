package com.dabblepoint.travelagent.analytics

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant

@Component
class AnalyticsClient(
    @Qualifier("analyticsRestClient") private val restClient: RestClient
) {

    @CircuitBreaker(name = "analytics")
    fun popularRoutes(limit: Int = 5): PopularRoutesResponse =
        restClient.get()
            .uri("/api/v1/analytics/popular-routes?limit={limit}", limit)
            .retrieve()
            .body(PopularRoutesResponse::class.java)
            ?: PopularRoutesResponse(Instant.now(), emptyList())
}