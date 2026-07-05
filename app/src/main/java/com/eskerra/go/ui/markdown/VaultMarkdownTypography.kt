package com.eskerra.go.ui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextLinkStyles
import com.eskerra.go.ui.theme.EskerraHeadingH1
import com.eskerra.go.ui.theme.EskerraHeadingH2
import com.eskerra.go.ui.theme.EskerraHeadingH3
import com.eskerra.go.ui.theme.EskerraHeadingH4
import com.eskerra.go.ui.theme.EskerraHeadingH5
import com.eskerra.go.ui.theme.EskerraHeadingH6
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography

/**
 * Markdown typography pinned to the shared Eskerra heading ladder (spec §8) so notes, the today hub,
 * and the inbox detail renderer all size headings identically. Non-heading styles keep the library
 * defaults; [textLink] overrides link styling when provided (the inbox detail renderer needs it).
 */
@Composable
fun vaultMarkdownTypography(textLink: TextLinkStyles? = null): MarkdownTypography =
    if (textLink != null) {
        markdownTypography(
            h1 = EskerraHeadingH1,
            h2 = EskerraHeadingH2,
            h3 = EskerraHeadingH3,
            h4 = EskerraHeadingH4,
            h5 = EskerraHeadingH5,
            h6 = EskerraHeadingH6,
            textLink = textLink
        )
    } else {
        markdownTypography(
            h1 = EskerraHeadingH1,
            h2 = EskerraHeadingH2,
            h3 = EskerraHeadingH3,
            h4 = EskerraHeadingH4,
            h5 = EskerraHeadingH5,
            h6 = EskerraHeadingH6
        )
    }
