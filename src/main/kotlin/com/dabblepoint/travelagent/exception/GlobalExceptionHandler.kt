package com.dabblepoint.travelagent.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice


@RestControllerAdvice
class GlobalExceptionHandler {


    @ExceptionHandler(WeatherApiException::class)
    fun handleWeatherApiException(ex: WeatherApiException) = ResponseEntity.status(502).body(
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Weather provider unavailable")
    )


}