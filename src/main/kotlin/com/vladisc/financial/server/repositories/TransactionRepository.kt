package com.vladisc.financial.server.repositories

import com.vladisc.financial.server.models.Transaction
import com.vladisc.financial.server.models.TransactionsTable
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
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
    fun findTransaction() {}
    fun getTransactions() {}
    fun updateTransaction() {}
    fun deleteTransaction() {}
}