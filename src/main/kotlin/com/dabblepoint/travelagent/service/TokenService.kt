package com.dabblepoint.travelagent.service

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.OctetSequenceKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

@Service
class TokenService(
    @Value("\${travel.security.jwt.secret}") secret: String,
    @Value("\${travel.security.jwt.ttl}") private val ttl: Duration,
) {
    private val encoder = NimbusJwtEncoder(
        ImmutableJWKSet<SecurityContext>(
            JWKSet(
                OctetSequenceKey.Builder(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
                    .build()
            )
        )
    )

    fun issue(auth: Authentication): Jwt {
        val header = JwsHeader.with(MacAlgorithm.HS256)
            .type("JWT")
            .build()
        val claims = JwtClaimsSet.builder()
            .subject(auth.name)
            .id(
                UUID.randomUUID()
                    .toString()
            )
            .claim("roles", auth.authorities.map { it.authority })
            .issuedAt(Instant.now())
            .expiresAt(
                Instant.now()
                    .plusSeconds(ttl.seconds)
            )
            .build()
        return encoder.encode(JwtEncoderParameters.from(header, claims))
    }
}
