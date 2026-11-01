package com.eskerra.go.core.notifications

/** Matches reminder occurrences conservatively when surrounding text or duplicates change. */
object NotificationEventMatching {
    private data class ReminderKey(val group: String, val anchor: String, val suffix: String)

    private fun parse(key: String): ReminderKey? {
        val fields = key.split(':')
        if (fields.size != 6 || fields[0] != "reminder") return null
        return ReminderKey(fields[1], fields[2], fields[5])
    }

    fun reminderGroup(event: NotificationEvent): String? = parse(event.key)?.group

    /** Returns current key to previous event; ambiguous duplicate matches are intentionally omitted. */
    fun match(
        previous: Map<String, NotificationEvent>,
        current: List<NotificationEvent>
    ): Map<String, NotificationEvent> {
        val result = mutableMapOf<String, NotificationEvent>()
        for (event in current) previous[event.key]?.let { result[event.key] = it }
        val oldGroups = previous.values.filter {
            parse(it.key) != null
        }.groupBy { parse(it.key)!!.group }
        val newGroups = current.filter { parse(it.key) != null }.groupBy { parse(it.key)!!.group }
        for ((group, newItems) in newGroups) {
            val oldItems = oldGroups[group].orEmpty()
            if (oldItems.size == 1 && newItems.size == 1) {
                result[newItems.single().key] = oldItems.single()
                continue
            }
            val oldByAnchor = oldItems.groupBy { parse(it.key)!!.anchor }
            val newByAnchor = newItems.groupBy { parse(it.key)!!.anchor }
            for ((anchor, newMatches) in newByAnchor) {
                val oldMatches = oldByAnchor[anchor].orEmpty()
                if (newMatches.size == 1 && oldMatches.size == 1) {
                    result[newMatches.single().key] = oldMatches.single()
                }
            }
        }
        return result
    }
}
