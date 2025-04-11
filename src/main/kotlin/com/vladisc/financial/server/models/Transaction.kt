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
    val payDate = datetime("pay_date").nullable()
    val invoiceStatus = enumerationByName("invoice_status", 15, InvoiceStatus::class).nullable()

    override val primaryKey = PrimaryKey(id)
}

enum class TransactionType { INCOME, EXPENSE, INVOICE, REFUND, TRANSFER, DIVIDEND }

enum class InvoiceStatus {
    /** Automatically confirmed. Before due_date: CONFIRMED, after due_date: PAID. Add pay_date when PAID */
    CONFIRMED,
    /** Automatically not confirmed. Before due_date: UNCONFIRMED, after due_date: UNPAID */
    UNCONFIRMED,
    /** When invoice has been canceled. Set manually. Keep due_date, remove pay_date */
    CANCELED,
    /** Set to status PAID either manually or if it was CONFIRMED and over due_date. Add pay_date */
    PAID,
    /** Set to status UNPAID either manually or if it was UNCONFIRMED and over due_date */
    UNPAID
}
enum class EditedBy { AUTO, USER }

@Serializable
data class Transaction(
    val id: String? = null,
    val timestamp: String? = null,
    val amount: Float? = null,
    val name: String? = null,
    val type: TransactionType? = null,
    val editedBy: EditedBy? = null,
    val dueDate: String? = null,
    val payDate: String? = null,
    val invoiceStatus: InvoiceStatus? = null
)

data class TransactionQueryParameters(
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
)