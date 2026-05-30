package com.eskerra.go.core.model

sealed interface WikiLinkResolution {
    val link: WikiLink
}

data class ResolvedWikiLink(override val link: WikiLink, val note: NoteSummary) :
    WikiLinkResolution

data class MissingWikiLink(override val link: WikiLink, val reason: MissingWikiLinkReason) :
    WikiLinkResolution

data class AmbiguousWikiLink(override val link: WikiLink, val candidates: List<NoteSummary>) :
    WikiLinkResolution

enum class MissingWikiLinkReason {
    EmptyTarget,
    PathTraversal,
    NoMatch
}
