package com.trinimate.services

import com.trinimate.db.*
import com.trinimate.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.*

class PlayerService {

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    fun userResponse(row: ResultRow, myLat: Double? = null, myLng: Double? = null): UserResponse {
        val id = row[Users.id]
        val sports = transaction {
            UserSports.select { UserSports.userId eq id }
                .map { SportLevel(it[UserSports.sport], it[UserSports.level]) }
        }
        // REAL stats: rating from the ratings table (averaged on each rate),
        // sessions = count of sessions the user joined
        val sessionsPlayed = transaction {
            SessionParticipants.select { SessionParticipants.userId eq id }.count().toInt()
        }
        val dist = if (myLat != null && myLng != null && row[Users.latitude] != null)
            haversineKm(myLat, myLng, row[Users.latitude]!!, row[Users.longitude]!!) else null
        return UserResponse(
            id = id, name = row[Users.name], city = row[Users.city],
            country = row[Users.country], bio = row[Users.bio],
            avatarUrl = row[Users.avatarUrl], phone = row[Users.phone],
            rating = row[Users.rating], sessions = sessionsPlayed, responseRate = 90,
            sports = sports, distanceKm = dist?.let { (it * 10).roundToInt() / 10.0 },
        )
    }

    fun nearby(lat: Double, lng: Double, radiusKm: Double, sport: String?, excludeId: Long): List<UserResponse> = transaction {
        val bbox = radiusKm / 111.0 // rough degree conversion
        val query = Users.select {
            (Users.latitude greaterEq (lat - bbox)) and
                    (Users.latitude lessEq (lat + bbox)) and
                    (Users.longitude greaterEq (lng - bbox)) and
                    (Users.longitude lessEq (lng + bbox)) and
                    (Users.id neq excludeId)
        }
        query.map { it to userResponse(it, lat, lng) }
            .filter { (_, u) ->
                (u.distanceKm ?: 9999.0) <= radiusKm &&
                        (sport == null || u.sports.any { it.sport.equals(sport, ignoreCase = true) })
            }
            .sortedBy { (_, u) -> u.distanceKm }
            .map { (_, u) -> u }
    }

    fun profile(userId: Long): UserResponse = transaction {
        userResponse(Users.select { Users.id eq userId }.single())
    }

    fun updateProfile(userId: Long, req: UpdateProfileRequest): UserResponse = transaction {
        // 1. Validate sport names against the Sports table (before changing anything)
        req.sports?.forEach { s ->
            val known = Sports.select { Sports.name eq s.sport }.count() > 0
            require(known) { "Unknown sport: ${s.sport}" }
        }

        // 2. Update the basic profile fields
        Users.update({ Users.id eq userId }) { st ->
            req.name?.let { st[name] = it }
            req.city?.let { st[city] = it }
            req.bio?.let { st[bio] = it }
            req.latitude?.let { st[latitude] = it }
            req.longitude?.let { st[longitude] = it }
            req.radiusKm?.let { st[radiusKm] = it }
        }

        // 3. Replace the user's sports
        req.sports?.let { list ->
            UserSports.deleteWhere { UserSports.userId eq userId }
            list.forEach { s ->
                UserSports.insert {
                    it[UserSports.userId] = userId
                    it[UserSports.sport] = s.sport
                    it[UserSports.level] = s.level
                }
            }
        }

        userResponse(Users.select { Users.id eq userId }.single())
    }

    /** Toggles a ping. Returns {"pinged": true} when created, false when removed. */
    fun togglePing(from: Long, to: Long, message: String?): Map<String, Boolean> = transaction {
        require(from != to) { "You cannot ping yourself" }
        require(Users.select { Users.id eq to }.count() > 0) { "User not found" }

        val existing = Pings.select { (Pings.fromUser eq from) and (Pings.toUser eq to) }.singleOrNull()
        if (existing != null) {
            if (existing[Pings.status] == "accepted") return@transaction mapOf("pinged" to true)
            Pings.deleteWhere { Pings.id eq existing[Pings.id] }
            return@transaction mapOf("pinged" to false)
        }
        val fromName = Users.select { Users.id eq from }.single()[Users.name]
        Pings.insert {
            it[fromUser] = from; it[toUser] = to; it[Pings.message] = message
            it[createdAt] = System.currentTimeMillis()
        }
        Notifications.insert {
            it[Notifications.userId] = to
            it[type] = "ping"
            it[refId] = from                       // the sender's user id
            it[title] = "New ping!"
            it[body] = "$fromName wants to play with you."
            it[createdAt] = System.currentTimeMillis()
        }
        mapOf("pinged" to true)
    }

    fun acceptPingFrom(fromUserId: Long, me: Long, chats: ChatService): Long = transaction {
        val updated = Pings.update({
            (Pings.fromUser eq fromUserId) and (Pings.toUser eq me) and (Pings.status eq "pending")
        }) { it[status] = "accepted" }
        require(updated == 1) { "Ping not found" }

        val myName = Users.select { Users.id eq me }.single()[Users.name]
        Notifications.insert {
            it[Notifications.userId] = fromUserId
            it[type] = "ping"
            it[refId] = me
            it[title] = "Ping accepted!"
            it[body] = "$myName accepted your ping. Say hello!"
            it[createdAt] = System.currentTimeMillis()
        }
        chats.getOrCreateChat(me, fromUserId)
    }
    fun setAvatar(userId: Long, url: String?): String? = transaction {
        val old = Users.select { Users.id eq userId }.single()[Users.avatarUrl]
        Users.update({ Users.id eq userId }) { it[Users.avatarUrl] = url }
        old
    }
}