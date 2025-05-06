package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.*
import com.vladisc.financial.server.models.Transaction
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.let

class TransactionRepository {
    fun addTransaction(t: Transaction, uid: Int): Int? {
        // Generate transaction id based on date, name, amount
        return transaction {
            val inserted = TransactionsTable.insertIgnore {
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
            val insertedRows = inserted.resultedValues
            insertedRows?.size?.let {
                if (it > 0) {
                    insertedRows[0][TransactionsTable.id]
                } else {
                    null
                }
            }

        }
    }

    fun addTransactions(transactions: List<Transaction>, uid: Int): List<Int> {
        return transaction {
            transactions.mapNotNull {
                addTransaction(it, uid)
            }
        }
    }

    fun findTransaction(transactionId: Int): ResultRow? {
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

    fun updateTransaction(t: Transaction, transactionId: Int): Boolean {
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
        } catch (_: Exception) {
            return false
        }
    }

    fun changeInvoiceStatus(transactionId: Int, invoiceStatus: InvoiceStatus): Boolean {
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

        } catch (_: Exception) {
            return false
        }
    }

    fun deleteTransaction(transactionId: Int): Boolean {
        return try {
            transaction {
                val deletedRows = TransactionsTable.deleteWhere { id eq transactionId }
                deletedRows > 0
            }
        } catch (_: Exception) {
            false
        }
    }
}