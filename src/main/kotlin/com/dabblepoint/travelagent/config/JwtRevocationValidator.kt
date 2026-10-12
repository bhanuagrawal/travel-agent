package com.dabblepoint.travelagent.config

import com.dabblepoint.travelagent.service.TokenDenylist
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

class JwtRevocationValidator(private val denylist: TokenDenylist) : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult =
        if (denylist.isRevoked(token.id))
            OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Token has been revoked", null))
        else
            OAuth2TokenValidatorResult.success()
}