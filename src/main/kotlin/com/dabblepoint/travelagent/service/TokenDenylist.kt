package com.dabblepoint.travelagent.service


import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class TokenDenylist(private val redis: StringRedisTemplate) {

    fun revoke(jwt: Jwt) {
        val remaining = Duration.between(Instant.now(), jwt.expiresAt)
        if (!remaining.isNegative) {
            redis.opsForValue().set("auth:revoked:${jwt.id}", "1", remaining)
        }
    }

    fun isRevoked(tokenId: String?): Boolean =
        tokenId != null && redis.hasKey("auth:revoked:$tokenId")
}