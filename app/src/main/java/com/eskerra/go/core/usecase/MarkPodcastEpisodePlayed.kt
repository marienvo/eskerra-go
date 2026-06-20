package com.eskerra.go.core.usecase

data class MarkPodcastEpisodePlayedResult(val content: String, val updated: Boolean)

class MarkPodcastEpisodePlayed {
    operator fun invoke(content: String, mp3Url: String): MarkPodcastEpisodePlayedResult =
        markPodcastEpisodeAsPlayedInContent(content, mp3Url)
}

fun markPodcastEpisodeAsPlayedInContent(
    content: String,
    mp3Url: String
): MarkPodcastEpisodePlayedResult {
    if (mp3Url.isEmpty()) {
        return MarkPodcastEpisodePlayedResult(content = content, updated = false)
    }

    var lineStart = 0
    while (lineStart < content.length) {
        val newlineIndex = content.indexOf('\n', startIndex = lineStart)
        val lineEnd = if (newlineIndex == -1) content.length else newlineIndex
        val line = content.substring(lineStart, lineEnd)

        if (line.contains(mp3Url)) {
            val updatedLine = UNPLAYED_CHECKBOX_REGEX.replaceFirst(line, "$1x$2")
            if (updatedLine == line) {
                return MarkPodcastEpisodePlayedResult(content = content, updated = false)
            }
            return MarkPodcastEpisodePlayedResult(
                content = content.substring(0, lineStart) +
                    updatedLine +
                    content.substring(lineEnd),
                updated = true
            )
        }

        if (newlineIndex == -1) break
        lineStart = newlineIndex + 1
    }

    return MarkPodcastEpisodePlayedResult(content = content, updated = false)
}

private val UNPLAYED_CHECKBOX_REGEX = Regex("""^(\s*-\s*\[)\s(\]\s+)""")
