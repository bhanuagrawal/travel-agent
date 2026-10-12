package com.dabblepoint.travelagent.config

import com.dabblepoint.travelagent.service.TokenDenylist
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers("/mcp")
                    .permitAll()
                    .requestMatchers("/api/v1/agent/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, res, _ ->
                    res.status = 401; res.contentType = "application/json"
                    res.writer.write("""{"error":"Authentication required"}""")
                }
                it.accessDeniedHandler { _, res, _ ->
                    res.status = 403; res.contentType = "application/json"
                    res.writer.write("""{"error":"Insufficient permissions"}""")
                }
            }
            .oauth2ResourceServer { resource ->
                resource.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(rolesJwtAuthenticationConverter())
                }
            }
        return http.build()
    }

    @Bean
    fun userDetailsService(): UserDetailsService = InMemoryUserDetailsManager(
        User.withUsername("user")
            .password("{bcrypt}\$2y\$10\$c32nmlg551MaksbBMvdpV.9nnbC910hnq8QymcJ3LZg0Al6AvG1Au")
            .roles("USER")
            .build(),
        User.withUsername("admin")
            .password("{bcrypt}\$2y\$10\$.Xt7PUxoEEdkUvRjvQ51XuV4w/BMyF2/yGewp8V0Y.CpobDkKCB3G")
            .roles("USER", "ADMIN")
            .build()
    )

    @Bean
    fun daoAuthenticationProvider(uds: UserDetailsService): DaoAuthenticationProvider = DaoAuthenticationProvider(uds)

    @Bean
    fun authenticationManager(provider: DaoAuthenticationProvider): AuthenticationManager = ProviderManager(provider)

    @Bean
    fun jwtDecoder(
        denyList: TokenDenylist,
        @Value("\${travel.security.jwt.secret}") secret: String
    ): JwtDecoder {
        val decoder = NimbusJwtDecoder.withSecretKey(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            .build()
        decoder.setJwtValidator(
            JwtValidators.createDefaultWithValidators(
                JwtTimestampValidator(),
                JwtRevocationValidator(denyList),
            )
        )

        return decoder
    }


    @Bean
    fun rolesJwtAuthenticationConverter(): JwtAuthenticationConverter = JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter { jwt ->
            val roles = jwt.getClaimAsStringList("roles") ?: emptyList()
            roles.map { SimpleGrantedAuthority(it) }
        }
    }
}