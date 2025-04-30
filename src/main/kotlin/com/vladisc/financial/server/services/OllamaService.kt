package com.vladisc.financial.server.services

import com.vladisc.financial.server.models.Notification
import com.vladisc.financial.server.models.Transaction
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate


@Serializable
data class TransactionWithNotification(
    val transaction: Transaction,
    val notification: Notification?
)

@Serializable
data class OllamaRequest(val model: String, val prompt: String, val stream: Boolean = false)

class OllamaService {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000 // how long the entire request can take
            connectTimeoutMillis = 60_000 // how long to wait when connecting
            socketTimeoutMillis = 120_000 // how long to wait for a read/write operation
        }
        engine {
            config {
                retryOnConnectionFailure(true)
            }
        }
    }

    suspend fun extractTransactions(
        notifications: List<Notification>,
        firstName: String,
        lastName: String,
        companyName: String,
        prevTransactions: List<Transaction>,
        prevNotifications: List<Notification>
    ): List<Transaction>? {
        try {
            val mergedTransactionsAndNotifications =
                mergeTransactionsAndNotifications(prevTransactions, prevNotifications)
            val mergedTransactionsAndNotificationsList = mergedTransactionsAndNotifications.joinToString(";\n") {
                val transactionJson = Json.encodeToString(it.transaction)
                "From the notification: \"${it.notification?.title}. ${it.notification?.body}\" the following transaction \"$transactionJson\" has been created"
            }
            println("mergedTransactionsAndNotificationsList $mergedTransactionsAndNotificationsList")
            val name = "$firstName $lastName".uppercase()
            val companyNameRule = if (companyName.isNotBlank()) {
                "- My company that pays me monthly income is ${companyName.uppercase()}. If transaction contains this name, it means that I am getting a salary income."
            } else {
                ""
            }

            val notificationsString = notifications.joinToString(", ", prefix = "[", postfix = "]") {
                "\"${it.title}. ${it.body}\""
            }
            println("notificationsString $notificationsString")
            val prompt = """
    Extract structured transactions from each of these banking push notifications: $notificationsString
    
    **Output Format (JSON, no extra text):** 
    {
      "amount": <amount as Float>,
      "name": "<exact legal entity name of company or venue or person name>",
      "type": "<INCOME | EXPENSE | INVOICE | TRANSFER | DIVIDEND>"
      "dueDate": <date time in YYYY-MM-DDTHH:mm:SS format, where time is 23:59:59 if not stated otherwise | null if date is not stated at all>
      "invoiceStatus": <UNCONFIRMED | PAID>
    }

    **Some extra rules to take into consideration when identifying between TRANSFER or DIVIDEND**
    $companyNameRule
    - My name is $name. If transaction contains this name, it means, I am getting dividends paid. 
    - If transaction contain other person name or other company name, it means, that's a transfer.
    - If transaction contains "Income. VIPPS MOBILEPAY AS ..." - means I am getting a transfer from some unknown person, so type `TRANSFER`.
    
    **Classification Rules with examples:** 
    - **INVOICE:** (examples:, "Unconfirmed invoice. Due date is 10.4.2025: 16,99 € to Vattenfall Oy. You can edit the payment details", "Unconfirmed invoice. Due date is today: 60.00 € to DNA OYJ. You can edit the payment details", "E-invoice. 25 € paid to payee Rakennusliito ry.", "Payment. Paid 750 € to payee KeskinäinenTyöeläkevakuutus.")
    - **TRANSFER:** (examples: "Income. ALEXANDER CHER paid 300 €", "Income. VIPPS MOBILEPAY AS, paid 12,50€")
    - **INCOME:** (examples:, "Income. RB GLOBAL paid 3232,23 €")
    - **DIVIDEND:** (examples:, "Income. $name paid 18.18 €")
    - **EXPENSE:** (examples:, "Card payment (Credit). You paid 35.05 € to payee Starbucks")
    
    **Extra rules for identification if the `INVOICE` status**
    - `UNCONFIRMED` can be only when it is clearly seen from the notification, that it is unconfirmed. (examples: "Unconfirmed invoice. Due date is 10.4.2025: 16,99 € to Vattenfall Oy. You can edit the payment details")
    - `PAID` can be only when it is E-invoice or Payment, which is paid and seen in the notification text. (examples: "E-invoice. 25 € paid to payee Rakennusliito ry.", "Payment. Paid 750 € to payee KeskinäinenTyöeläkevakuutus.")
    - If it is not clearly possible to define `CONFIRMED` or `UNCONFIRMED`, then it is `null`
     
    **Rules:**
    - Only return data in JSON format (array of objects), no explanation, no extra text.
    - For each object in the array:
        - Ensure `amount` is a **float** (examples:, `12.99`).
        - Ensure `name` is **exactly** the person/company/venue name (examples:, `"K-Market"`, `"Neste Oy"`, `"Kir Cedar"`).
        - Ensure `type` is **one of** `"INCOME"`, `"EXPENSE"`, or `"INVOICE", or "TRANSFER", or "DIVIDEND"`.
        - Ensure `dueDate` is **stated** and not `null` only if it is an INVOICE type, and the date can be defined from the text. If text states the due date is today or tomorrow, then set the corresponding date. Today is ${LocalDate.now()} Else `null`. Date should be in format "YYYY-MM-DD'T'HH:mm:ss".
        - Ensure `invoiceStatus` is **one of** `"CONFIRMED"`, `"UNCONFIRMED"`, `"CANCELED"`, `"PAID"`, `"UNPAID"`.
        
""".trimIndent()
            println(prompt)

            val requestBody = OllamaRequest(model = "qwen2.5-coder", prompt = prompt)

            val response: HttpResponse = client.post("http://localhost:11434/api/generate") {
                setBody(json.encodeToString(OllamaRequest.serializer(), requestBody))
            }

            val responseBody: String = response.body()
//            println("response body $responseBody")
            // Collect all "response" fields and merge into one string
            val fullResponse = responseBody
                .trim()
                .lines()
                .mapNotNull { line ->
                    try {
                        json.parseToJsonElement(line).jsonObject["response"]?.jsonPrimitive?.content
                    } catch (e: Exception) {
                        println("Skipping invalid JSON: $line")
                        null
                    }
                }
                .joinToString("").trimIndent()
            println("full response $fullResponse")
            val jsonArrayText = extractJsonArray(fullResponse)
            val transactions = jsonArrayText?.let {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<List<Transaction>>(it)
            }
            println(transactions)
            return transactions
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractJsonArray(text: String): String? {
        var startIndex = -1
        var bracketCount = 0

        for (i in text.indices) {
            when (text[i]) {
                '[' -> {
                    if (bracketCount == 0) {
                        startIndex = i
                    }
                    bracketCount++
                }
                ']' -> {
                    bracketCount--
                    if (bracketCount == 0 && startIndex != -1) {
                        return text.substring(startIndex, i + 1)
                    }
                }
            }
        }

        return null
    }

    private fun mergeTransactionsAndNotifications(
        transactions: List<Transaction>,
        notifications: List<Notification>
    ): List<TransactionWithNotification> {
        val notificationMap = notifications.associateBy { it.transactionId }
        return transactions.map { transaction ->
            TransactionWithNotification(
                transaction = transaction,
                notification = notificationMap[transaction.id]
            )
        }
    }


}
