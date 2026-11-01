package com.eskerra.go.core.notifications

import com.eskerra.go.core.markdown.DateToken
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Read-only port of the desktop daemon's live reminder token grammar. */
object ReminderCandidates {
    private val token = Regex(
        """(?:^|[\s\uFEFF\p{Z}])(@[0-9]{4}-[0-9]{2}-[0-9]{2}""" +
            """(?:_[0-9]{4}(?:-[0-9]{4})?)?)"""
    )

    private data class Candidate(val start: String, val anchor: String, val due: Long)

    fun scan(noteId: String, markdown: String, zone: ZoneId): List<NotificationEvent> {
        val candidates = token.findAll(markdown).mapNotNull { match ->
            val group = match.groups[1] ?: return@mapNotNull null
            val text = group.value
            val start = text.substringBeforeLast('-', text).takeIf { text.length == 21 } ?: text
            val parsed = DateToken.parseDateToken(start) ?: return@mapNotNull null
            if (text.length == 21) {
                val end = text.takeLast(4)
                if (end.substring(0, 2).toInt() > 23 || end.substring(2, 4).toInt() > 59) {
                    return@mapNotNull null
                }
            }
            val lineStart = markdown.lastIndexOf('\n', group.range.first - 1) + 1
            val nextLine = markdown.indexOf('\n', group.range.last + 1)
            val lineEnd = if (nextLine < 0) markdown.length else nextLine
            val masked = markdown.substring(lineStart, group.range.first) + "\u0000TOKEN\u0000" +
                markdown.substring(group.range.last + 1, lineEnd).removeSuffix("\r")
            val due = LocalDate.of(parsed.year, parsed.month, parsed.day)
                .atTime(LocalTime.of(parsed.hour ?: 9, parsed.minute ?: 0))
                .atZone(zone).toInstant().toEpochMilli()
            Candidate(start, sha256(masked), due)
        }.toList()
        val counts = candidates.groupingBy { it.start }.eachCount()
        val anchors = candidates.groupingBy { it.start to it.anchor }.eachCount()
        val noteHash = sha256(markdown)
        val ordinals = mutableMapOf<Pair<String, String>, Int>()
        return candidates.map { candidate ->
            val group = candidate.start to candidate.anchor
            val ordinal = ordinals[group] ?: 0
            ordinals[group] = ordinal + 1
            val groupId = sha256("$noteId\u0000${candidate.start}")
            val duplicateCount = counts.getValue(candidate.start)
            val suffix = if (anchors.getValue(group) > 1) noteHash else "stable"
            val key = "reminder:$groupId:${candidate.anchor}:$duplicateCount:$ordinal:$suffix"
            NotificationEvent(key, noteId, NotificationEventType.REMINDER, candidate.due)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
