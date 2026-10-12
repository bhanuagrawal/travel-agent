package com.dabblepoint.travelagent.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

@Service
class RefreshTokenStore(
    private val redis: StringRedisTemplate,
    @Value("\${travel.security.refresh.ttl}") private val ttl: Duration,
) {
    private fun key(token: String) = "auth:refresh:${sha256(token)}"

    fun issue(username: String): String {
        val token = UUID.randomUUID().toString()
        redis.opsForValue().set(key(token), username, ttl)
        return token
    }

    fun consume(token: String): String? {
        val k = key(token)
        val username = redis.opsForValue().get(k) ?: return null
        redis.delete(k)                                      // rotation: single-use
        return username
    }

    fun revoke(token: String): Boolean = redis.delete(key(token))

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}