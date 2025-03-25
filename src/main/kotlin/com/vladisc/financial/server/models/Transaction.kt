package com.vladisc.financial.server.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object TransactionsTable : Table("transactions") {
    val id = varchar("id", 100)
    val userId = integer("user_id").references(UsersTable.id)
    val timestamp = datetime("date_time")
    val amount = decimal("amount", 10, 2)
    val name = varchar("name", 255)

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class Transaction(
    val timestamp: String,
    val amount: Float,
    val name: String,
)


@Serializable
data class PartialTransaction(
    val timestamp: String? = null,
    val amount: Float? = null,
    val name: String? = null,
)