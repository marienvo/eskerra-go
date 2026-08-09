package com.eskerra.go.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.share.BuildShareDraft
import com.eskerra.go.core.share.PendingShare
import com.eskerra.go.core.share.SharePrefill
import com.eskerra.go.core.share.ShareTitleUpgrade
import com.eskerra.go.core.usecase.FetchSharedPageTitle
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Turns an inbound share into compose-pill prefills.
 *
 * Emits the immediate draft at once, then — only for a bare URL with no usable subject — a
 * second prefill once the page title has been fetched. A ViewModel rather than a composable
 * effect so an in-flight fetch, and the record of which share was already handled, both survive
 * a rotation.
 */
class ShareIntakeViewModel(private val fetchSharedPageTitle: FetchSharedPageTitle) : ViewModel() {

    private val _prefills = Channel<SharePrefill>(Channel.BUFFERED)
    val prefills = _prefills.receiveAsFlow()

    private var lastHandledShareId = 0L
    private var titleJob: Job? = null

    fun offer(share: PendingShare) {
        if (share.id <= lastHandledShareId) {
            return
        }
        lastHandledShareId = share.id
        // A newer share supersedes whatever title was still being fetched for the previous one.
        titleJob?.cancel()

        val draft = BuildShareDraft.build(share.content) ?: return
        _prefills.trySend(
            SharePrefill(
                token = share.id,
                stage = SharePrefill.Stage.Immediate,
                text = draft.text,
                caretOffset = draft.caretOffset
            )
        )

        val url = draft.titleFetchUrl ?: return
        titleJob = viewModelScope.launch {
            // The fetcher contract is "never throws", but a title is a nicety: a misbehaving
            // implementation must not take the app down over a share that already works.
            val title = runCatching { fetchSharedPageTitle(url) }.getOrNull() ?: return@launch
            val upgraded = ShareTitleUpgrade.apply(draft.text, title) ?: return@launch
            _prefills.trySend(
                SharePrefill(
                    token = share.id,
                    stage = SharePrefill.Stage.TitleUpgrade,
                    text = upgraded.text,
                    caretOffset = upgraded.caretOffset
                )
            )
        }
    }

    companion object {
        fun factory(fetchSharedPageTitle: FetchSharedPageTitle): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ShareIntakeViewModel(fetchSharedPageTitle) as T
            }
    }
}
