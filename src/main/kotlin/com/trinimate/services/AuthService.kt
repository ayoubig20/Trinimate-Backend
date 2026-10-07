package com.trinimate.services

import com.trinimate.db.Users
import com.trinimate.models.*
import com.trinimate.security.JwtConfig
import com.trinimate.security.PasswordHasher
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Locale

class AuthService(private val hasher: PasswordHasher) {

    fun register(req: RegisterRequest): TokenResponse {
        require(req.password.length >= 6) { "Password must be at least 6 characters" }
        val email = req.email.lowercase(Locale.ROOT)
        val existing = transaction { Users.select { Users.email eq email }.count() }
        require(existing == 0L) { "Email already registered" }

        val id = transaction {
            Users.insert {
                it[Users.email] = email
                it[passwordHash] = hasher.hash(req.password)
                it[name] = req.name
                it[city] = req.city
                it[createdAt] = System.currentTimeMillis()
            } get Users.id
        }
        return TokenResponse(JwtConfig.accessToken(id), JwtConfig.refreshToken(id))
    }

    fun login(req: LoginRequest): TokenResponse {
        val row = transaction {
            Users.select { Users.email eq req.email.lowercase(Locale.ROOT) }.singleOrNull()
        } ?: throw IllegalArgumentException("Invalid email or password")
        require(hasher.verify(req.password, row[Users.passwordHash])) { "Invalid email or password" }
        val id = row[Users.id]
        return TokenResponse(JwtConfig.accessToken(id), JwtConfig.refreshToken(id))
    }

    fun refresh(refreshToken: String): TokenResponse {
        val decoded = try {
            com.auth0.jwt.JWT.require(com.auth0.jwt.algorithms.Algorithm.HMAC256(JwtConfig.secret))
                .withAudience(JwtConfig.audience).withIssuer(JwtConfig.issuer).build()
                .verify(refreshToken)
        } catch (e: Exception) { throw IllegalArgumentException("Invalid refresh token") }
        require(decoded.getClaim("type").asString() == "refresh") { "Invalid refresh token" }
        val id = decoded.getClaim("userId").asLong()
        return TokenResponse(JwtConfig.accessToken(id), JwtConfig.refreshToken(id))
    }

    /** Dev mode: returns the reset token in the response. Wire an email sender in production. */
    fun forgotPassword(email: String): Map<String, String> {
        val row = transaction { Users.select { Users.email eq email }.singleOrNull() }
            ?: return mapOf("message" to "If that email exists, a reset link is on its way.")
        return mapOf(
            "message" to "If that email exists, a reset link is on its way.",
            "devResetToken" to JwtConfig.resetToken(row[Users.id]),
        )
    }
}