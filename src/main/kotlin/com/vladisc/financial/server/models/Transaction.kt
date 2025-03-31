package com.vladisc.financial.server.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object TransactionsTable : Table("transactions") {
    val id = varchar("id", 100)
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val timestamp = datetime("date_time")
    val amount = decimal("amount", 10, 2)
    val name = varchar("name", 255)
    val type = enumerationByName("type", 10, TransactionType::class).nullable()
    val editedBy = enumerationByName("edited_by", 10, EditedBy::class)
    val dueDate = datetime("due_date").nullable()
    val invoiceStatus = enumerationByName("invoice_status", 15, InvoiceStatus::class).nullable()

    override val primaryKey = PrimaryKey(id)
}

enum class TransactionType { INCOME, EXPENSE, INVOICE, REFUND, TRANSFER, DIVIDEND }
enum class InvoiceStatus { CONFIRMED, UNCONFIRMED, CANCELED, PAID, UNPAID }
enum class EditedBy { AUTO, USER }

@Serializable
data class Transaction(
    val timestamp: String? = null,
    val amount: Float? = null,
    val name: String? = null,
    val type: TransactionType? = null,
    val editedBy: EditedBy? = null,
    val dueDate: String? = null,
    val invoiceStatus: InvoiceStatus? = null
)

data class TransactionQueryParameters(
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
)