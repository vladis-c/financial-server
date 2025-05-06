package com.vladisc.financial.server.routing.transaction

import com.vladisc.financial.server.models.*
import io.ktor.http.*
import org.jetbrains.exposed.sql.ResultRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object TransactionRoutingUtil {
        fun parseTransaction(transactionRow: ResultRow): Transaction {
        return Transaction(
            timestamp = transactionRow[TransactionsTable.timestamp].toString(),
            amount = transactionRow[TransactionsTable.amount].toFloat(),
            name = transactionRow[TransactionsTable.name],
            type = transactionRow[TransactionsTable.type],
            editedBy = transactionRow[TransactionsTable.editedBy],
            dueDate = transactionRow[TransactionsTable.dueDate].toString(),
            payDate = transactionRow[TransactionsTable.payDate].toString(),
            invoiceStatus = transactionRow[TransactionsTable.invoiceStatus]
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

