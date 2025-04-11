package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.*
import com.vladisc.financial.server.models.Transaction
import com.vladisc.financial.server.routing.transaction.TransactionRoutingUtil
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class TransactionRepository {
    fun addTransaction(t: Transaction, uid: Int): String? {
        // Generate transaction id based on date, name, amount
        val transactionId =
            TransactionRoutingUtil.generateTransactionId(t.timestamp)
        return transaction {
            val inserted = TransactionsTable.insertIgnore {
                it[id] = transactionId
                it[userId] = uid
                if (!t.timestamp.isNullOrBlank()) {
                    it[timestamp] = LocalDateTime.parse(t.timestamp)
                } else {
                    it[timestamp] = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                }
                if (t.amount != null) {
                    it[amount] = t.amount.toBigDecimal()
                }
                if (!t.name.isNullOrBlank()) {
                    it[name] = t.name
                }
                /// not mandatory
                it[type] = t.type
                it[editedBy] = t.editedBy ?: EditedBy.USER
                it[dueDate] = t.dueDate?.let { date -> LocalDateTime.parse(date) }
                it[payDate] = t.payDate?.let { date -> LocalDateTime.parse(date) }
                it[invoiceStatus] = t.invoiceStatus
            }
            if (inserted.insertedCount > 0) {
                transactionId
            } else {
                null
            }

        }
    }

    fun addTransactions(transactions: List<Transaction>, uid: Int): List<String> {
        return transaction {
            transactions.mapNotNull {
                addTransaction(it, uid)
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

    fun getLatestTransactionsByType(uid: Int): List<ResultRow> {
        return transaction {
            TransactionsTable
                .selectAll().where { TransactionsTable.userId eq uid }
                .orderBy(TransactionsTable.timestamp, SortOrder.DESC)
                .groupBy { it[TransactionsTable.type] to it[TransactionsTable.invoiceStatus] }
                .map { it.value.first() } // Take the latest per group
        }
    }

    fun updateTransaction(t: Transaction, transactionId: String): Boolean {
        try {
            return transaction {
                val updateStatement = TransactionsTable.update({ TransactionsTable.id eq transactionId }) {
                    if (!t.timestamp.isNullOrBlank()) {
                        it[timestamp] = LocalDateTime.parse(t.timestamp)
                    }
                    if (t.amount != null) {
                        it[amount] = t.amount.toBigDecimal()
                    }
                    if (!t.name.isNullOrBlank()) {
                        it[name] = t.name
                    }
                    if (t.type != null) {
                        it[type] = t.type
                    }
                    if (t.editedBy != null) {
                        it[editedBy] = t.editedBy
                    }
                    if (!t.dueDate.isNullOrBlank()) {
                        it[dueDate] = LocalDateTime.parse(t.dueDate)
                    }
                    if (!t.payDate.isNullOrBlank()) {
                        it[payDate] = LocalDateTime.parse(t.payDate)
                    }
                    if (t.invoiceStatus != null) {
                        it[invoiceStatus] = t.invoiceStatus
                    }

                }
                return@transaction updateStatement != 0
            }
        } catch (e: Exception) {
            return false
        }
    }

    fun changeInvoiceStatus(transactionId: String, invoiceStatus: InvoiceStatus): Boolean {
        try {
            return transaction {
                val updateStatement = TransactionsTable.update({ TransactionsTable.id eq transactionId }) {
                    if (invoiceStatus == InvoiceStatus.PAID) {
                        it[payDate] = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                    } else {
                        it[payDate] = null
                    }
                    it[TransactionsTable.invoiceStatus] = invoiceStatus
                    it[editedBy] = EditedBy.USER
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