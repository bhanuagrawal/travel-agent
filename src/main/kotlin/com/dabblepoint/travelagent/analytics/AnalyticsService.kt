package com.dabblepoint.travelagent.analytics

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException

@Service
class AnalyticsService(
    private val analyticsClient: AnalyticsClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun trendingRoutes(limit: Int = 5): List<PopularRoute>? =
        try {
            analyticsClient.popularRoutes(limit).routes
        } catch (ex: CallNotPermittedException) {
            log.warn("Analytics circuit breaker OPEN — trending unavailable")
            null
        } catch (ex: RestClientException) {
            log.warn("Analytics call failed ({}): {} — trending unavailable",
                ex.javaClass.simpleName, ex.message)
            null
        }
}