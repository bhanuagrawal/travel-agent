package com.dabblepoint.travelagent.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient


@Configuration
class RestClientConfig {

    @Bean
    fun openMeteoRestClient(): RestClient =
        RestClient.builder()
            .baseUrl("https://api.open-meteo.com/v1")
            .build()

    @Bean
    fun openSkyRestClient(): RestClient =
        RestClient.builder()
            .baseUrl("https://opensky-network.org/api")
            .build()
}