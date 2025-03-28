package com.vladisc.financial.server.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object TransactionsTable : Table("transactions") {
    val id = varchar("id", 100)
    val userId = integer("user_id").references(UsersTable.id)
    val timestamp = datetime("date_time")
    val amount = decimal("amount", 10, 2)
    val name = varchar("name", 255)
    val notificationId =
        varchar("notification_id", 100).uniqueIndex().references(NotificationTable.id) // Links to NotificationsTable
    val type = enumerationByName("type", 10, TransactionType::class)
    val editedBy = enumerationByName("edited_by", 10, EditedBy::class)
    val completed = bool("completed").default(false)

    override val primaryKey = PrimaryKey(id)
}

enum class TransactionType { INCOME, EXPENSE, INVOICE }

enum class EditedBy { AUTO, USER }

@Serializable
data class Transaction(
    val timestamp: String,
    val amount: Float,
    val name: String,
    val type: TransactionType,
    val editedBy: EditedBy,
    val completed: Boolean,
)


@Serializable
data class PartialTransaction(
    val timestamp: String? = null,
    val amount: Float? = null,
    val name: String? = null,
    val type: TransactionType? = null,
    val editedBy: EditedBy? = null,
    val completed: Boolean? = null,
)

data class TransactionQueryParameters(
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
)