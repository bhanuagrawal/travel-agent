package com.dabblepoint.travelagent.rag

import org.slf4j.LoggerFactory
import org.springframework.ai.reader.TextReader
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class PolicyIngestor(private val vectorStore: SimpleVectorStore) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val documents = TextReader(ClassPathResource("docs/flight-policies.md")).read()
        val chunks = TokenTextSplitter.builder().build().apply(documents)   // builder, not ctor
        vectorStore.add(chunks)
        log.info("Ingested {} chunks into the vector store", chunks.size)
    }
}