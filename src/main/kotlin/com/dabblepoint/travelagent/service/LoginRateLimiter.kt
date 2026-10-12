package com.dabblepoint.travelagent.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class LoginRateLimiter(private val redis: StringRedisTemplate) {

    fun tooManyAttempts(username: String): Boolean =
        (redis.opsForValue().get("auth:fail:$username")?.toInt() ?: 0) >= 5

    fun recordFailure(username: String) {
        val key = "auth:fail:$username"
        val n = redis.opsForValue().increment(key) ?: 0L
        if (n == 1L) redis.expire(key, Duration.ofMinutes(15))   // window starts at first failure
    }

    fun reset(username: String) = redis.delete("auth:fail:$username")   // successful login clears it
}