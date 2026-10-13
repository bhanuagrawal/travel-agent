package com.dabblepoint.travelagent.model

import com.dabblepoint.travelagent.analytics.PopularRoute

data class FlightSearchResponse(
    val flightSummary: String,
    val trending: List<PopularRoute>?
)