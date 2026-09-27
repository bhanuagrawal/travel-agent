package com.dabblepoint.travelagent.config

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RagConfig {
    @Bean
    fun vectorStore(embeddingModel: EmbeddingModel): SimpleVectorStore =
        SimpleVectorStore.builder(embeddingModel).build()
}