package com.trinimate.models

import kotlinx.serialization.Serializable

@Serializable data class RegisterRequest(val name: String, val email: String, val password: String, val city: String? = null)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class ForgotRequest(val email: String)
@Serializable data class TokenResponse(val accessToken: String, val refreshToken: String)

@Serializable data class UserResponse(
    val id: Long, val name: String, val city: String? = null, val bio: String? = null,
    val avatarUrl: String? = null, val rating: Double = 0.0, val sessions: Int = 0,
    val responseRate: Int = 0, val sports: List<SportLevel> = emptyList(),
    val distanceKm: Double? = null,
)
@Serializable data class SportLevel(val sport: String, val level: String)
@Serializable data class UpdateProfileRequest(
    val name: String? = null, val city: String? = null, val bio: String? = null,
    val latitude: Double? = null, val longitude: Double? = null, val radiusKm: Double? = null,
    val sports: List<SportLevel>? = null,
)
@Serializable data class PingRequest(val message: String? = null)

@Serializable data class SessionRequest(
    val sport: String, val title: String, val dateTime: Long,
    val place: String, val fee: String? = null, val playersNeeded: Int,
    val latitude: Double? = null, val longitude: Double? = null,
    val visibleToCommunity: Boolean = true,
)
@Serializable data class SessionResponse(
    val id: Long, val sport: String, val title: String, val dateTime: Long,
    val place: String, val fee: String? = null, val playersNeeded: Int,
    val playersJoined: Int, val createdBy: Long,
)

@Serializable data class ChatResponse(val id: Long, val peer: UserResponse, val lastMessage: String, val unread: Boolean)
@Serializable data class MessageRequest(val text: String)
@Serializable data class MessageResponse(val id: Long, val chatId: Long, val senderId: Long, val text: String, val sentAt: Long, val isMine: Boolean = false)

@Serializable data class EventResponse(val id: Long, val title: String, val place: String, val eventTime: Long, val sport: String? = null)
@Serializable data class NotificationResponse(val id: Long, val type: String, val title: String, val body: String, val read: Boolean, val createdAt: Long)
@Serializable data class ErrorResponse(val error: String)