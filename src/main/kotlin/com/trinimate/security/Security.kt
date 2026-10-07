package com.trinimate.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.util.Date
import io.ktor.server.config.*

object JwtConfig {
    lateinit var secret: String; private set
    lateinit var issuer: String; private set
    lateinit var audience: String; private set
    lateinit var realm: String; private set

    fun init(config: ApplicationConfig) {
        secret = config.property("secret").getString()
        issuer = config.property("issuer").getString()
        audience = config.property("audience").getString()
        realm = config.property("realm").getString()
    }

    fun createToken(userId: Long, type: String, ttlMillis: Long): String =
        JWT.create()
            .withAudience(audience).withIssuer(issuer)
            .withClaim("userId", userId).withClaim("type", type)
            .withExpiresAt(Date(System.currentTimeMillis() + ttlMillis))
            .sign(Algorithm.HMAC256(secret))

    fun accessToken(userId: Long) = createToken(userId, "access", 60L * 60 * 1000)
    fun refreshToken(userId: Long) = createToken(userId, "refresh", 30L * 24 * 3600 * 1000)
    fun resetToken(userId: Long) = createToken(userId, "reset", 15L * 60 * 1000)
}

fun Application.configureSecurity() {
    JwtConfig.init(environment.config.config("jwt"))
    install(Authentication) {
        jwt("auth-jwt") {
            realm = JwtConfig.realm
            verifier(
                JWT.require(Algorithm.HMAC256(JwtConfig.secret))
                    .withAudience(JwtConfig.audience)
                    .withIssuer(JwtConfig.issuer)
                    .acceptLeeway(3)
                    .build()
            )
            validate { credential ->
                val type = credential.payload.getClaim("type").asString()
                if (type == "access") JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respondText(
                    """{"error":"Token invalid or expired"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized,
                )
            }
        }
    }
}

val ApplicationCall.userId: Long
    get() = principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()