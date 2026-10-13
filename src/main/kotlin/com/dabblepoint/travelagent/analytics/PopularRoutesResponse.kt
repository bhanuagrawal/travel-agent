package com.dabblepoint.travelagent.analytics


import java.time.Instant

data class PopularRoutesResponse(
    val generatedAt: Instant,
    val routes: List<PopularRoute>
)

data class PopularRoute(
    val origin: String,
    val destination: String,
    val searches: Long,
    val lastSeen: Instant
)