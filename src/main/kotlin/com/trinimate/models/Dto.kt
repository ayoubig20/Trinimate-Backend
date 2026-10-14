package com.trinimate.models

import kotlinx.serialization.Serializable

@Serializable data class RegisterRequest(val name: String, val email: String, val password: String,
                                         val city: String? = null, val country: String? = null,
                                         val phone: String? = null,
                                         val latitude: Double? = null, val longitude: Double? = null,)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class ForgotRequest(val email: String)
@Serializable data class TokenResponse(val accessToken: String, val refreshToken: String)

@Serializable data class UserResponse(
    val id: Long, val name: String, val city: String? = null,
    val country: String? = null,val phone: String? = null, val bio: String? = null,
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
@Serializable data class EventRequest(
    val title: String, val place: String, val eventTime: Long,
    val sport: String? = null, val latitude: Double? = null, val longitude: Double? = null,
)
@Serializable data class RateRequest(val stars: Int, val userId: Long)
@Serializable data class SessionRequest(
    val sport: String, val title: String, val dateTime: Long,
    val place: String, val fee: String? = null, val price: Double? = null,
    val playersNeeded: Int,
    val latitude: Double? = null, val longitude: Double? = null,
    val visibleToCommunity: Boolean = true,
    val durationMinutes: Int? = null,
    val level: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val equipment: List<String>? = null,
)
@Serializable data class SessionResponse(
    val id: Long, val sport: String, val title: String, val dateTime: Long,
    val place: String, val fee: String? = null,val price: Double? = null, val playersNeeded: Int,
    val playersJoined: Int,    val durationMinutes: Int? = null,
    val level: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val equipment: String? = null, val createdBy: Long,
)

@Serializable data class MessageRequest(val text: String,val kind: String = "text")
@Serializable data class MessageResponse(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val text: String,
    val sentAt: Long,
    val isMine: Boolean = false,
    val kind: String = "text",
    val reactions: Map<String, Int> = emptyMap(),
)


@Serializable data class EventResponse(val id: Long, val title: String, val place: String, val eventTime: Long, val sport: String? = null, val joinedCount: Int = 0,)
@Serializable data class NotificationResponse(val id: Long, val type: String, val title: String, val body: String, val read: Boolean, val createdAt: Long,val refId: Long? = null)
@Serializable data class ErrorResponse(val error: String)
@Serializable data class ChatListResponse(val id: Long, val peerId: Long, val peerName: String, val lastMessage: String, val lastTime: Long, val unread: Boolean = false, val peerAvatarUrl: String? = null, val sessionId: Long? = null,)
@Serializable data class SportResponse(val id: Int, val name: String)