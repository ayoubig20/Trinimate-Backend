package com.trinimate.db

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 512)
    val name = varchar("name", 128)
    val city = varchar("city", 128).nullable()
    val bio = text("bio").nullable()
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val radiusKm = double("radius_km").default(5.0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object UserSports : Table("user_sports") {
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val sport = varchar("sport", 64)
    val level = varchar("level", 32) // Beginner / Intermediate / Advanced
    override val primaryKey = PrimaryKey(userId, sport)
}

object Sessions : Table("sessions") {
    val id = long("id").autoIncrement()
    val sport = varchar("sport", 64)
    val title = varchar("title", 255)
    val dateTime = long("date_time") // epoch millis
    val place = varchar("place", 255)
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val fee = varchar("fee", 128).nullable()
    val playersNeeded = integer("players_needed")
    val createdBy = reference("created_by", Users.id)
    val visibleToCommunity = bool("visible").default(true)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SessionParticipants : Table("session_participants") {
    val sessionId = reference("session_id", Sessions.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val joinedAt = long("joined_at")
    override val primaryKey = PrimaryKey(sessionId, userId)
}

object Pings : Table("pings") {
    val id = long("id").autoIncrement()
    val fromUser = reference("from_user", Users.id)
    val toUser = reference("to_user", Users.id)
    val message = varchar("message", 512).nullable()
    val status = varchar("status", 16).default("pending") // pending / accepted / declined
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Chats : Table("chats") {
    val id = long("id").autoIncrement()
    val userA = reference("user_a", Users.id)
    val userB = reference("user_b", Users.id)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Messages : Table("messages") {
    val id = long("id").autoIncrement()
    val chatId = reference("chat_id", Chats.id, onDelete = ReferenceOption.CASCADE)
    val senderId = reference("sender_id", Users.id)
    val text = text("text")
    val sentAt = long("sent_at")
    override val primaryKey = PrimaryKey(id)
}

object Events : Table("events") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 255)
    val place = varchar("place", 255)
    val eventTime = long("event_time")
    val sport = varchar("sport", 64).nullable()
    override val primaryKey = PrimaryKey(id)
}

object Notifications : Table("notifications") {
    val id = long("id").autoIncrement()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 32)
    val title = varchar("title", 255)
    val body = text("body")
    val read = bool("read").default(false)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}