package com.trinimate.services

import com.trinimate.db.*
import com.trinimate.models.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.*
class MiscService {
    fun events(): List<EventResponse> = transaction {
        Events.selectAll().orderBy(Events.eventTime to SortOrder.ASC).map {
            EventResponse(it[Events.id], it[Events.title], it[Events.place],
                it[Events.eventTime], it[Events.sport])
        }
    }

    fun notifications(userId: Long): List<NotificationResponse> = transaction {
        Notifications.select { Notifications.userId eq userId }
            .orderBy(Notifications.createdAt, SortOrder.DESC).map {
                NotificationResponse(it[Notifications.id], it[Notifications.type],
                    it[Notifications.title], it[Notifications.body],
                    it[Notifications.read], it[Notifications.createdAt])
            }
    }

    fun notify(userId: Long, type: String, title: String, body: String) = transaction {
        Notifications.insert {
            it[Notifications.userId] = userId; it[Notifications.type] = type
            it[Notifications.title] = title; it[Notifications.body] = body
            it[createdAt] = System.currentTimeMillis()
        }
    }
}