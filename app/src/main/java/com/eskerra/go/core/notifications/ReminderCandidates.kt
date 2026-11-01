package com.eskerra.go.core.notifications

import com.eskerra.go.core.markdown.DateToken
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Read-only port of the desktop daemon's live reminder token grammar. */
object ReminderCandidates {
    private val token = Regex(
        """(?:^|[\s\uFEFF])(@[0-9]{4}-[0-9]{2}-[0-9]{2}""" +
            """(?:_[0-9]{4}(?:-[0-9]{4})?)?)"""
    )

    fun scan(noteId: String, markdown: String, zone: ZoneId): List<NotificationEvent> {
        val ordinals = mutableMapOf<String, Int>()
        return token.findAll(markdown).mapNotNull { match ->
            val text = match.groupValues[1]
            val dateAndTime =
                text.substringBeforeLast('-', text).takeIf { text.length == 21 } ?: text
            val parsed = DateToken.parseDateToken(dateAndTime) ?: return@mapNotNull null
            if (text.length == 21) {
                val end = text.takeLast(4)
                if (end.substring(0, 2).toInt() > 23 || end.substring(2, 4).toInt() > 59) {
                    return@mapNotNull null
                }
            }
            val start = dateAndTime
            val ordinal = ordinals[start] ?: 0
            ordinals[start] = ordinal + 1
            val hour = parsed.hour ?: 9
            val minute = parsed.minute ?: 0
            val due = LocalDate.of(parsed.year, parsed.month, parsed.day)
                .atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
            val identity = "$noteId\u0000$start\u0000$ordinal"
            val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            val key = digest.joinToString("") { "%02x".format(it) }
            NotificationEvent("reminder:$key", noteId, NotificationEventType.REMINDER, due)
        }.toList()
    }
}
