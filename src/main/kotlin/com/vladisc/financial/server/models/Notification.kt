package com.vladisc.financial.server.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object NotificationTable : Table("notifications") {
    val id = varchar("id", 100)
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val transactionId = varchar("transaction_id", 100).uniqueIndex().references(TransactionsTable.id,
        onDelete = ReferenceOption.CASCADE)
    val timestamp = datetime("date_time")
    val title = varchar("title", 255)
    val body = text("body")

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class Notification(
    val timestamp: String? = null,
    val title: String? = null,
    val body: String? = null,
)

data class NotificationQueryParameters(
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
)