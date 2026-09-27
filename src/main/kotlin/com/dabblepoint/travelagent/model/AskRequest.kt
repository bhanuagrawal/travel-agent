package com.dabblepoint.travelagent.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size


data class AskRequest(
    @field:NotBlank
    val conversationId: String,

    @field:NotBlank
    @field:Size(min = 1, max = 1000)
    val question: String
)