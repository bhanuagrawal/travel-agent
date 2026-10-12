package com.dabblepoint.travelagent.controller

import com.dabblepoint.travelagent.service.LoginRateLimiter
import com.dabblepoint.travelagent.service.RefreshTokenStore
import com.dabblepoint.travelagent.service.TokenDenylist
import com.dabblepoint.travelagent.service.TokenService
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class LoginRequest(val username: String, val password: String)

@RestController
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val tokenService: TokenService,
    private val rateLimiter: LoginRateLimiter,
    private val refreshTokenStore: RefreshTokenStore,
    private val userDetailsService: UserDetailsService,
    private val denyList: TokenDenylist
) {
    @PostMapping("/api/auth/login")
    fun login(@RequestBody req: LoginRequest): ResponseEntity<Map<String, String>> {

        if (rateLimiter.tooManyAttempts(req.username))
            return ResponseEntity.status(429).body(mapOf("error" to "Too many attempts — try later"))


        val authRequest = UsernamePasswordAuthenticationToken.unauthenticated(req.username, req.password)
        return try {
            val auth = authenticationManager.authenticate(authRequest)
            rateLimiter.reset(req.username)
            ResponseEntity.ok(
                mapOf(
                    "token" to tokenService.issue(auth).tokenValue,
                    "refreshToken" to refreshTokenStore.issue(auth.name),
                )
            )
        } catch (e: AuthenticationException) {
            rateLimiter.recordFailure(req.username)
            ResponseEntity.status(401).body(mapOf("error" to "Invalid username or password"))
        }
    }


    @PostMapping("/api/auth/refresh")
    fun refresh(@RequestBody body: Map<String, String>): ResponseEntity<Map<String, String>> {
        val username = refreshTokenStore.consume(body["refreshToken"] ?: "")
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Invalid or expired refresh token"))

        val user = userDetailsService.loadUserByUsername(username)
        val auth = UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities)
        return ResponseEntity.ok(
            mapOf(
                "token" to tokenService.issue(auth).tokenValue,
                "refreshToken" to refreshTokenStore.issue(username),
            )
        )
    }

    @PostMapping("/api/auth/logout")
    fun logout(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody body: Map<String, String>
    ): ResponseEntity<Void> {
        refreshTokenStore.revoke(body["refreshToken"] ?: "")
        denyList.revoke(jwt)
        return ResponseEntity.noContent().build()
    }
}