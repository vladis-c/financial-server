package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.Notification
import com.vladisc.financial.server.models.NotificationQueryParameters
import com.vladisc.financial.server.models.NotificationTable
import com.vladisc.financial.server.routing.notification.NotificationRoutingUtil
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class NotificationRepository {
    fun addNotification(n: Notification, uid: Int): String? {
        val notificationId = NotificationRoutingUtil.generateNotificationId(n)

        return transaction {
            val inserted = NotificationTable.insertIgnore {
                it[id] = notificationId
                it[userId] = uid
                it[timestamp] = LocalDateTime.parse(n.timestamp)
                it[title] = n.title
                it[body] = n.body
            }
            if (inserted.insertedCount > 0) {
                notificationId
            } else {
                null
            }
        }
    }

    fun getNotifications(uid: Int, queryParameters: NotificationQueryParameters): List<ResultRow> {
        val (startDate, endDate) = queryParameters
        val notificationList = transaction {
            NotificationTable.selectAll().where {
                (NotificationTable.userId eq uid) and
                        when {
                            startDate != null && endDate != null -> NotificationTable.timestamp.between(
                                startDate,
                                endDate
                            )

                            startDate != null -> NotificationTable.timestamp.greaterEq(startDate)
                            endDate != null -> NotificationTable.timestamp.lessEq(endDate)
                            else -> Op.TRUE
                        }
            }.sortedByDescending { NotificationTable.timestamp }.toList()
        }
        return notificationList
    }
}