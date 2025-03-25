package com.vladisc.financial.server.routing.transaction

import com.vladisc.financial.server.models.Transaction
import com.vladisc.financial.server.models.TransactionsTable
import org.jetbrains.exposed.sql.ResultRow
import java.security.MessageDigest

object TransactionRoutingUtil {
    fun generateTransactionId(timestamp: String, name: String, amount: String): String {
        val input = "$timestamp-$name-$amount"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(20) // First 20 chars
    }

    fun parseTransaction(transactionRow: ResultRow): Transaction {
        return Transaction(
            transactionRow[TransactionsTable.timestamp].toString(),
            transactionRow[TransactionsTable.amount].toFloat(),
            transactionRow[TransactionsTable.name],
        )
    }

    fun parseTransactions(transactionRows: List<ResultRow>): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        for (transactionRow in transactionRows) {
            transactions.add(parseTransaction(transactionRow))
        }
        return transactions
    }
}