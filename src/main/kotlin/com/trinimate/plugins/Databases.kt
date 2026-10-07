package com.trinimate.plugins

import com.trinimate.db.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
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
        SchemaUtils.create(
            Users, UserSports, Sessions, SessionParticipants,
            Pings, Chats, Messages, Events, Notifications,
        )
    }
    log.info("Database connected and schema created.")
}