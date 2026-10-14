package com.trinimate.db

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 512)
    val name = varchar("name", 128)
    val city = varchar("city", 128).nullable()
    val country = varchar("country", 128).nullable()
    val phone = varchar("phone", 32).nullable()
    val bio = text("bio").nullable()
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val radiusKm = double("radius_km").default(5.0)
    val rating = double("rating").default(0.0)
    val createdAt = long("created_at")
    init { index(false, latitude, longitude) }
    override val primaryKey = PrimaryKey(id)
}

object UserSports : Table("user_sports") {
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val sport = varchar("sport", 64)
    val level = varchar("level", 32)
    override val primaryKey = PrimaryKey(userId, sport)
}

object Sessions : Table("sessions") {
    val id = long("id").autoIncrement()
    val sport = varchar("sport", 64)
    val title = varchar("title", 255)
    val dateTime = long("date_time")
    val place = varchar("place", 255)
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val fee = varchar("fee", 128).nullable()
    val price = double("price").nullable()
    val playersNeeded = integer("players_needed")
    val createdBy = reference("created_by", Users.id)
    val visibleToCommunity = bool("visible").default(true)
    val createdAt = long("created_at")
    init { index(false, dateTime) }
    val durationMinutes = integer("duration_minutes").nullable()
    val levelRequired = varchar("level_required", 32).nullable()
    val description = text("description").nullable()
    val tags = text("tags").nullable()          // comma-separated
    val equipment = text("equipment").nullable() // comma-separated
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
    val status = varchar("status", 16).default("pending")
    val createdAt = long("created_at")
    init { uniqueIndex(fromUser, toUser) }
    override val primaryKey = PrimaryKey(id)
}

object Chats : Table("chats") {
    val id = long("id").autoIncrement()
    // 1:1 chats fill both; group (session) chats leave them null
    val userA = reference("user_a", Users.id).nullable()
    val userB = reference("user_b", Users.id).nullable()
    val sessionId = reference("session_id", Sessions.id).nullable() // non-null = group chat
    val createdAt = long("created_at")
    init { uniqueIndex(sessionId) } // one group chat per session (NULLs are allowed multiple times)
    override val primaryKey = PrimaryKey(id)
}
object ChatMembers : Table("chat_members") {
    val chatId = reference("chat_id", Chats.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val joinedAt = long("joined_at")
    val leftAt = long("left_at").nullable() // null = active member
    override val primaryKey = PrimaryKey(chatId, userId)
}
object Messages : Table("messages") {
    val id = long("id").autoIncrement()
    val chatId = reference("chat_id", Chats.id, onDelete = ReferenceOption.CASCADE)
    val senderId = reference("sender_id", Users.id)
    val text = text("text")
    val kind = varchar("kind", 16).default("text")
    val sentAt = long("sent_at")
    init { index(false, chatId, sentAt) }
    override val primaryKey = PrimaryKey(id)
}

object Events : Table("events") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 255)
    val place = varchar("place", 255)
    val eventTime = long("event_time")
    val sport = varchar("sport", 64).nullable()
    val createdBy = reference("created_by", Users.id).nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    override val primaryKey = PrimaryKey(id)
}

object EventParticipants : Table("event_participants") {
    val eventId = reference("event_id", Events.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val joinedAt = long("joined_at")
    override val primaryKey = PrimaryKey(eventId, userId)
}

object Notifications : Table("notifications") {
    val id = long("id").autoIncrement()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 32)
    val refId = long("ref_id").nullable()
    val title = varchar("title", 255)
    val body = text("body")
    val read = bool("read").default(false)
    val createdAt = long("created_at")
    init { index(false, userId, createdAt) }
    override val primaryKey = PrimaryKey(id)
}

object Reactions : Table("reactions") {
    val messageId = reference("message_id", Messages.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val emoji = varchar("emoji", 8)
    override val primaryKey = PrimaryKey(messageId, userId, emoji)
}

object Ratings : Table("ratings") {
    val sessionId = reference("session_id", Sessions.id, onDelete = ReferenceOption.CASCADE)
    val raterId = reference("rater_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val ratedId = reference("rated_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val stars = integer("stars")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(sessionId, raterId, ratedId)
}
object Sports : Table("sports") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 64).uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}

/** Last message each user has read in each chat. */
object ChatReads : Table("chat_reads") {
    val chatId = reference("chat_id", Chats.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val lastReadMessageId = long("last_read_message_id").default(0)
    override val primaryKey = PrimaryKey(chatId, userId)
}