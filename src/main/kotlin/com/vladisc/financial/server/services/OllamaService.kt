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


            val prompt =
                "Extract amount and store name from this notification: \"${notification.body}\". Return JSON {\"amount\": <amount>, \"name\": \"<venue>\"}. Return only object, no extra text"
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
            json.decodeFromString(PartialTransaction.serializer(), response)
        } catch (e: Exception) {
            null
        }
    }
}
