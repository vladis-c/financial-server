package com.vladisc.financial.server.services

import com.vladisc.financial.server.models.Notification
import com.vladisc.financial.server.models.PartialTransaction
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.engine.okhttp.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class OllamaRequest(val model: String, val prompt: String, val stream: Boolean = false)

class OllamaService {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp)

    suspend fun extractTransaction(notification: Notification): PartialTransaction? {
        try {
            val prompt = """
    Extract structured financial data from this banking notification: "${notification.body}".
    
    **Output Format (JSON, no extra text):** 
    {
      "amount": <amount as Float>,
      "name": "<exact legal entity name of company or venue>",
      "type": "<INCOME | EXPENSE | INVOICE>"
    }

    **Classification Rules:**
    - **INCOME:** Payment received (e.g., "Taiste Oy paid 3000", "Salary from Google: 5000")
    - **EXPENSE:** Money spent (e.g., "You paid 35.05 to K-Market", "Card purchase at Starbucks: 5.99")
    - **INVOICE:** Pending payment (e.g., "Unconfirmed invoice: Vattenfall Oy 25,00. Due date 10.4.2025")
    
    **Rules:**
    - Only return JSON, no explanation.
    - Ensure `amount` is a **float** (e.g., `12.99`).
    - Ensure `name` is **exactly** the company/venue name (e.g., `"K-Market"`, `"Taiste Oy"`).
    - Ensure `type` is **one of** `"INCOME"`, `"EXPENSE"`, or `"INVOICE"`.
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

    private fun extractTransactionFromResponse(response: String): PartialTransaction? {
        return try {
            println("Parsing Transaction: $response") // Debugging
            json.decodeFromString(PartialTransaction.serializer(), response)
        } catch (e: Exception) {
            null
        }
    }
}
