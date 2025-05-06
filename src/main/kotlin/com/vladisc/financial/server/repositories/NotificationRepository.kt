package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.Notification
import com.vladisc.financial.server.models.NotificationQueryParameters
import com.vladisc.financial.server.models.NotificationTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class NotificationRepository {
    fun addNotification(n: Notification, uid: Int, transactionId: Int?): Int? {
        return transaction {
            val inserted = NotificationTable.insertIgnore {
                it[userId] = uid
                if (transactionId != null) {
                    it[NotificationTable.transactionId] = transactionId
                }
                if (!n.timestamp.isNullOrBlank()) {
                    it[timestamp] = LocalDateTime.parse(n.timestamp)
                }
                if (!n.title.isNullOrBlank()) {
                    it[title] = n.title
                }
                if (!n.body.isNullOrBlank()) {
                    it[body] = n.body
                }
            }
            val insertedRows = inserted.resultedValues
            insertedRows?.size?.let {
                if (it > 0) {
                    insertedRows[0][NotificationTable.id]
                } else {
                    null
                }
            }
        }
    }

    fun addNotifications(notifications: List<Notification>, uid: Int, transactionIds: List<Int>): List<Int> {
        return transaction {
            notifications.zip(transactionIds).mapNotNull { (notification, transactionId) ->
                addNotification(notification, uid, transactionId)
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

    fun getLastNotifications(uid: Int, transactionIds: List<Int>?): List<ResultRow> {
        return transaction {
            NotificationTable
                .selectAll().where {
                    (NotificationTable.userId eq uid) and
                            (if (transactionIds?.isNotEmpty() == true) {
                                NotificationTable.transactionId inList transactionIds
                            } else {
                                Op.TRUE
                            })
                }
                .orderBy(NotificationTable.timestamp, SortOrder.DESC)
                .toList()
        }
    }

    fun getNotificationByTransactionId(transactionId: Int): ResultRow? {
        val notificationsList = transaction {
            NotificationTable.selectAll().where {
                NotificationTable.transactionId eq transactionId
            }.toList()
        }

        return if (notificationsList.isEmpty()) {
            null
        } else {
            notificationsList[0]
        }
    }
}