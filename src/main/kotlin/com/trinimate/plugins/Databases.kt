package com.trinimate.plugins

import com.trinimate.db.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases() {
    val cfg = environment.config.config("database")
    val ds = HikariDataSource(HikariConfig().apply {
        jdbcUrl = cfg.property("url").getString()
        username = cfg.property("user").getString()
        password = cfg.property("password").getString()
        driverClassName = cfg.property("driver").getString()
        maximumPoolSize = 10
    })
    Database.connect(ds)

    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            Users, UserSports, Sessions, SessionParticipants,
            Pings, Chats, Messages, Events, EventParticipants,
            Notifications, Reactions, Ratings,Sports,ChatReads,ChatMembers
        )
        if (Sports.selectAll().count() == 0L) {
            val allSports = listOf(
                "Running", "Yoga", "Fitness", "Swimming", "Boxing", "Cycling", "Pilates",
                "Equitation", "Hiking", "Tennis", "Padel", "Badminton", "Squash",
                "Table Tennis", "Judo", "Fencing", "Golf", "Football", "Basketball",
                "Volleyball", "Handball", "Rugby", "Cricket", "Baseball",
            )
            val existingSports = Sports.selectAll().map { it[Sports.name] }.toSet()
            allSports.filter { it !in existingSports }.forEach { n ->
                Sports.insert { it[Sports.name] = n }
            }
        }
        // Seed demo events once, so the Community screen has data
        if (Events.selectAll().count() == 0L && Users.selectAll().count() > 0L) {
            val day = 24L * 3600 * 1000
            val now = System.currentTimeMillis()
            Events.insert {
                it[title] = "Agadir Padel Open — mixed doubles"
                it[place] = "Padel Club Agadir"
                it[eventTime] = now + 3 * day
                it[sport] = "Padel"
                it[createdBy] = 1
            }
            Events.insert {
                it[title] = "Community 5-a-side pickup game"
                it[place] = "City Arena Agadir"
                it[eventTime] = now + 10 * day
                it[sport] = "Football"
                it[createdBy] = 1
            }
            Events.insert {
                it[title] = "Sunrise group ride — 70 km"
                it[place] = "Marina Agadir"
                it[eventTime] = now + 18 * day
                it[sport] = "Cycling"
                it[createdBy] = 1
            }
        }
    }
    log.info("Database connected and schema created.")
}