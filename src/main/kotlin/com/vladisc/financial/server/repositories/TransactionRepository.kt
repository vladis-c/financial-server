package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.Transaction
import com.vladisc.financial.server.models.TransactionQueryParameters
import com.vladisc.financial.server.models.TransactionsTable
import com.vladisc.financial.server.routing.transaction.TransactionRoutingUtil
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class TransactionRepository {
    fun addTransaction(t: Transaction, uid: Int, notificationId: String?): String? {
        // Generate transaction id based on date, name, amount
        val transactionId =
            TransactionRoutingUtil.generateTransactionId(t)

        return transaction {
            val inserted = TransactionsTable.insertIgnore {
                it[id] = transactionId
                it[userId] = uid
                it[timestamp] = LocalDateTime.parse(t.timestamp)
                it[amount] = t.amount.toBigDecimal()
                it[name] = t.name
                it[TransactionsTable.notificationId] = notificationId ?: transactionId
                it[type] = t.type
                it[editedBy] = t.editedBy
                it[completed] = t.completed
            }
            if (inserted.insertedCount > 0) {
                transactionId
            } else {
                null
            }

        }
    }

    fun findTransaction(transactionId: String): ResultRow? {
        val transactionList = transaction {
            TransactionsTable.selectAll().where { TransactionsTable.id eq transactionId }.toList()
        }
        return if (transactionList.isEmpty()) {
            null
        } else {
            transactionList[0]
        }
    }

    fun getTransactions(uid: Int, queryParameters: TransactionQueryParameters): List<ResultRow> {
        val (startDate, endDate) = queryParameters
        val transactionList = transaction {
            TransactionsTable.selectAll().where {
                (TransactionsTable.userId eq uid) and
                        when {
                            startDate != null && endDate != null -> TransactionsTable.timestamp.between(
                                startDate,
                                endDate
                            )

                            startDate != null -> TransactionsTable.timestamp.greaterEq(startDate)
                            endDate != null -> TransactionsTable.timestamp.lessEq(endDate)
                            else -> Op.TRUE
                        }
            }
                .sortedByDescending { TransactionsTable.timestamp }.toList()
        }
        return transactionList
    }

    fun updateTransaction(transactionId: String, updates: TransactionsTable.(UpdateStatement) -> Unit): Boolean {
        try {
            return transaction {
                val updateStatement = TransactionsTable.update({ TransactionsTable.id eq transactionId }) {
                    updates(it)
                }
                return@transaction updateStatement != 0
            }
        } catch (e: Exception) {
            return false
        }
    }

    fun deleteTransaction(transactionId: String): Boolean {
        return try {
            transaction {
                val deletedRows = TransactionsTable.deleteWhere { id eq transactionId }
                deletedRows > 0
            }
        } catch (e: Exception) {
            false
        }
    }
}