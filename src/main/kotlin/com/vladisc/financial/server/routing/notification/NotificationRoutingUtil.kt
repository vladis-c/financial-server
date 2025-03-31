package com.vladisc.financial.server.routing.notification

import com.vladisc.financial.server.models.Notification
import com.vladisc.financial.server.models.NotificationQueryParameters
import com.vladisc.financial.server.models.NotificationTable
import io.ktor.http.*
import org.jetbrains.exposed.sql.ResultRow
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object NotificationRoutingUtil {
    fun generateNotificationId(n: Notification): String {
        val input = "${n.timestamp}-${n.title}-${n.body}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(20) // First 20 chars
    }

    private fun parseNotification(notificationRow: ResultRow): Notification {
        return Notification(
            notificationRow[NotificationTable.timestamp].toString(),
            notificationRow[NotificationTable.title],
            notificationRow[NotificationTable.body]
        )
    }

    fun parseNotifications(notificationRows: List<ResultRow>): List<Notification> {
        val notifications = mutableListOf<Notification>()
        for (notificationRow in notificationRows) {
            notifications.add(parseNotification(notificationRow))
        }
        return  notifications
    }

    fun getNotificationQueries(parameters: Parameters): NotificationQueryParameters {
        val startDateQ = parameters["start_date"]
        val endDateQ = parameters["end_date"]

        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val startDate = startDateQ?.let { LocalDate.parse(it, dateFormatter).atStartOfDay() }
        val endDate = endDateQ?.let { LocalDate.parse(it, dateFormatter).atTime(23, 59, 59) }

        val queryParameters = NotificationQueryParameters(startDate, endDate)
        return queryParameters
    }
}