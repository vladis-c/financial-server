package com.vladisc.financial.server.services

import com.vladisc.financial.server.models.Notification
import com.vladisc.financial.server.models.Transaction
import com.vladisc.financial.server.models.User
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.engine.okhttp.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

@Serializable
data class OllamaRequest(val model: String, val prompt: String, val stream: Boolean = false)

class OllamaService {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp)

    suspend fun extractTransaction(notification: Notification, firstName: String, lastName: String, companyName: String): Transaction? {
        try {
            val name = "$firstName $lastName".uppercase()
            val prompt = """
    Extract structured financial data from this banking notification: "${notification.title}. ${notification.body}".
    
    **Output Format (JSON, no extra text):** 
    {
      "amount": <amount as Float>,
      "name": "<exact legal entity name of company or venue or person name>",
      "type": "<INCOME | EXPENSE | INVOICE | TRANSFER | DIVIDEND>"
      "dueDate": <date time in YYYY-MM-DDTHH:mm:SS format, where time is 23:59:59 if not stated otherwise | null if date is not stated at all>
      "invoiceStatus": <CONFIRMED | UNCONFIRMED | CANCELED | PAID | UNPAID>
    }

    **Some extra rules to take into consideration when identifying between INCOME, TRANSFER or DIVIDEND**
    - My company that pays me monthly income is $companyName. If transaction contains this name, it means, that I am getting a salary income
    - My name is $name. If transaction contains this name, it means, I am getting dividends paid. 
    - If transaction contain other person name or other company name, it means, that's a transfer.
    
    **Extra rules for identification if the `invoice` status**
    - `CONFIRMED` can be only when it is clearly seen from the notification, that it is confirmed
    - `UNCONFIRMED` can be only when it is clearly seen from the notification, that it is unconfirmed
    - If it is not clearly possible to define `CONFIRMED` or `UNCONFIRMED`, then it is `null`
    
    **Classification Rules:**
    - **INCOME:** Payment received (e.g., "Income. TAISTE OY paid 3232,23 €")
    - **TRANSFER:** Payment received (e.g., "Income. ALEXANDER CHER paid 300 €", "Income. VIPPS MOBILEPAY AS, paid 12,50€")
    - **DIVIDEND:** Payment received (e.g., "Income. $name paid 18.18 €")
    - **EXPENSE:** Money spent (e.g., "Card payment (Credit). You paid $35.05 to payee Starbucks")
    - **INVOICE:** Pending payment (e.g., "Unconfirmed invoice: Due date is 10.4.2025: 16,99 € to Vattenfall Oy. You can edit the payment details", "Unconfirmed invoice: Due date is today: 60.00 € to DNA OYJ. You can edit the payment details", "Confirmed invoice: Due date is today: 30 € to Rakennusliito oy. You can edit the payment details")
    
    **Rules:**
    - Only return JSON, no explanation.
    - Ensure `amount` is a **float** (e.g., `12.99`).
    - Ensure `name` is **exactly** the person/company/venue name (e.g., `"K-Market"`, `"Taiste Oy"`, `"Kir Cedar"`).
    - Ensure `type` is **one of** `"INCOME"`, `"EXPENSE"`, or `"INVOICE", or "TRANSFER", or "DIVIDEND"`.
    - Ensure `dueDate` is **stated** and not `null` only if it is an INVOICE type, and the date can be defined from the text. If text states the due date is today or tomorrow, then set the corresponding date. Today is ${LocalDate.now()} Else `null`.
    - Ensure `invoiceStatus` is **one of** `"CONFIRMED"`, `"UNCONFIRMED"`, `"CANCELED"`, `"PAID"`, `"UNPAID"`.
    
""".trimIndent()

            val requestBody = OllamaRequest(model = "llama3.2", prompt = prompt)

            val response: HttpResponse = client.post("http://localhost:11434/api/generate") {
                setBody(json.encodeToString(OllamaRequest.serializer(), requestBody))
            }

            val responseBody: String = response.body()

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
                .joinToString("")

            return extractTransactionFromResponse(fullResponse)
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractTransactionFromResponse(response: String): Transaction? {
        return try {
            println("Parsing Transaction: $response") // Debugging
            json.decodeFromString(Transaction.serializer(), response)
        } catch (e: Exception) {
            null
        }
    }
}
