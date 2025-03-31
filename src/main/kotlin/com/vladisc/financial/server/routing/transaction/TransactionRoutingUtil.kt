package com.vladisc.financial.server.routing.transaction

import com.vladisc.financial.server.models.Transaction
import com.vladisc.financial.server.models.TransactionQueryParameters
import com.vladisc.financial.server.models.TransactionsTable
import io.ktor.http.*
import org.jetbrains.exposed.sql.ResultRow
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object TransactionRoutingUtil {
    fun generateTransactionId(t:Transaction): String {
        val input = "${t.timestamp}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(20) // First 20 chars
    }

    fun parseTransaction(transactionRow: ResultRow): Transaction {
        return Transaction(
            transactionRow[TransactionsTable.timestamp].toString(),
            transactionRow[TransactionsTable.amount].toFloat(),
            transactionRow[TransactionsTable.name],
            transactionRow[TransactionsTable.type],
            transactionRow[TransactionsTable.editedBy],
        )
    }

    fun parseTransactions(transactionRows: List<ResultRow>): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        for (transactionRow in transactionRows) {
            transactions.add(parseTransaction(transactionRow))
        }
        return transactions
    }

    fun getTransactionQueries(parameters: Parameters): TransactionQueryParameters {
        val startDateQ = parameters["start_date"]
        val endDateQ = parameters["end_date"]

        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val startDate = startDateQ?.let { LocalDate.parse(it, dateFormatter).atStartOfDay() }
        val endDate = endDateQ?.let { LocalDate.parse(it, dateFormatter).atTime(23, 59, 59) }

        val queryParams = TransactionQueryParameters(startDate, endDate)
        return queryParams
    }
}

