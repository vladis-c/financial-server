package com.vladisc.financial.server.routing.transaction

import java.security.MessageDigest

object TransactionUtil {
    fun generateTransactionId(timestamp: String, name: String, amount: String): String {
        val input = "$timestamp-$name-$amount"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(20) // First 20 chars
    }
}