package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.Transaction
import com.vladisc.financial.server.models.TransactionsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class TransactionRepository {
    fun addTransaction(t: Transaction, transactionId: String, uid: Int): Boolean {
        return transaction {
            val inserted = TransactionsTable.insertIgnore {
                it[id] = transactionId
                it[userId] = uid
                it[timestamp] = LocalDateTime.parse(t.timestamp)
                it[name] = t.name
                it[amount] = t.amount.toBigDecimal()
            }
            inserted.insertedCount > 0
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

    fun getTransactions(uid: Int): List<ResultRow> {
        val transactionList = transaction {
            TransactionsTable.selectAll().where { TransactionsTable.userId eq uid }
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

    fun deleteTransaction() {}
}