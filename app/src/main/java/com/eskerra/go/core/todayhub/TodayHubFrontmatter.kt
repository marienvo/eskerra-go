package com.eskerra.go.core.todayhub

/**
 * Today hub frontmatter parsing (spec §11.2). Mirrors
 * `packages/eskerra-core/src/todayHub/parseTodayHubFrontmatter.ts`.
 */
object TodayHubFrontmatter {

    /** Only `weekly` perpetual hubs are supported. */
    const val PERPETUAL_TYPE_WEEKLY = "weekly"

    /** Hub week start day; JS-style weekday number (Sunday = 0 … Saturday = 6). */
    enum class StartDay(val jsDay: Int) {
        SUNDAY(0),
        MONDAY(1),
        TUESDAY(2),
        WEDNESDAY(3),
        THURSDAY(4),
        FRIDAY(5),
        SATURDAY(6);

        companion object {
            fun fromName(value: String): StartDay? =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }

    data class Settings(
        val perpetualType: String = PERPETUAL_TYPE_WEEKLY,
        val columns: List<String> = emptyList(),
        val start: StartDay = StartDay.MONDAY
    )

    private val KEY_RE = Regex("""^([a-zA-Z0-9_]+)\s*:""")
    private val LIST_ITEM_RE = Regex("""^\s*-\s*(.*)$""")
    private val LIST_INDENT_RE = Regex("""^(\s*)-""")
    private val QUOTE_TRIM_RE = Regex("""^["']|["']$""")

    /** Editor columns = the date column plus one per frontmatter entry. */
    fun columnCount(settings: Settings): Int = 1 + settings.columns.size

    /**
     * Reads the first YAML frontmatter block only; unknown keys are ignored. `columns` may be a
     * single scalar (`columns: Name`) or a YAML list (`columns:` followed by `  - value` lines).
     */
    fun parse(markdown: String): Settings {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        var i = 0
        while (i < lines.size && lines[i].trim().isEmpty()) {
            i += 1
        }
        if (i >= lines.size || lines[i].trim() != "---") {
            return Settings()
        }
        i += 1
        val fmStart = i
        while (i < lines.size && lines[i].trim() != "---") {
            i += 1
        }
        if (i >= lines.size) {
            return Settings()
        }
        val fmLines = lines.subList(fmStart, i)

        var perpetualType = PERPETUAL_TYPE_WEEKLY
        var columns: List<String> = emptyList()
        var start = StartDay.MONDAY

        var j = 0
        while (j < fmLines.size) {
            val raw = fmLines[j]
            val key = normalizeKey(raw)
            if (key == null) {
                j += 1
                continue
            }
            when (key) {
                "perpetualtype" -> {
                    if (scalarAfterColon(raw).lowercase() == PERPETUAL_TYPE_WEEKLY) {
                        perpetualType = PERPETUAL_TYPE_WEEKLY
                    }
                }
                "start" -> {
                    StartDay.fromName(scalarAfterColon(raw))?.let { start = it }
                }
                "columns" -> {
                    val cols = mutableListOf<String>()
                    val inline = unquote(scalarAfterColon(raw).trim())
                    if (inline.isNotEmpty()) {
                        cols += inline
                    }
                    var k = j + 1
                    while (k < fmLines.size) {
                        val indent = LIST_INDENT_RE.containsMatchIn(fmLines[k])
                        val nextKey = normalizeKey(fmLines[k])
                        if (nextKey != null && !indent) {
                            break
                        }
                        LIST_ITEM_RE.find(fmLines[k])?.let { m ->
                            val item = unquote(m.groupValues[1].trim())
                            if (item.isNotEmpty()) {
                                cols += item
                            }
                        }
                        k += 1
                    }
                    columns = cols
                    j = k - 1
                }
            }
            j += 1
        }

        return Settings(perpetualType = perpetualType, columns = columns, start = start)
    }

    private fun normalizeKey(line: String): String? =
        KEY_RE.find(line.trim())?.groupValues?.get(1)?.lowercase()

    private fun scalarAfterColon(line: String): String {
        val idx = line.indexOf(':')
        if (idx == -1) return ""
        return line.substring(idx + 1).trim()
    }

    private fun unquote(value: String): String = value.replace(QUOTE_TRIM_RE, "")
}
