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
        val dist = if (myLat != null && myLng != null && row[Users.latitude] != null)
            haversineKm(myLat, myLng, row[Users.latitude]!!, row[Users.longitude]!!) else null
        return UserResponse(
            id = id, name = row[Users.name], city = row[Users.city], bio = row[Users.bio],
            avatarUrl = row[Users.avatarUrl], rating = 4.5, sessions = 0, responseRate = 90,
            sports = sports, distanceKm = dist?.let { (it * 10).roundToInt() / 10.0 },
        )
    }

    fun nearby(lat: Double, lng: Double, radiusKm: Double, sport: String?): List<UserResponse> = transaction {
        val bbox = radiusKm / 111.0 // rough degree conversion
        val query = Users.select {
            (Users.latitude greaterEq (lat - bbox)) and
                    (Users.latitude lessEq (lat + bbox)) and
                    (Users.longitude greaterEq (lng - bbox)) and
                    (Users.longitude lessEq (lng + bbox))
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
        Users.update({ Users.id eq userId }) { st ->
            req.name?.let { st[name] = it }
            req.city?.let { st[city] = it }
            req.bio?.let { st[bio] = it }
            req.latitude?.let { st[latitude] = it }
            req.longitude?.let { st[longitude] = it }
            req.radiusKm?.let { st[radiusKm] = it }
        }
        req.sports?.let { list ->
            UserSports.deleteWhere { UserSports.userId eq userId }
            list.forEach { s ->
                UserSports.insert {
                    it[UserSports.userId] = userId; it[sport] = s.sport; it[level] = s.level
                }
            }
        }
        userResponse(Users.select { Users.id eq userId }.single())
    }

    fun ping(from: Long, to: Long, message: String?) {
        require(from != to) { "You cannot ping yourself" }
        transaction {
            Pings.insert {
                it[fromUser] = from; it[toUser] = to; it[Pings.message] = message
                it[createdAt] = System.currentTimeMillis()
            }
        }
    }

    fun acceptPing(pingId: Long, userId: Long) = transaction {
        val updated = Pings.update({ (Pings.id eq pingId) and (Pings.toUser eq userId) }) {
            it[status] = "accepted"
        }
        require(updated == 1) { "Ping not found" }
    }
}