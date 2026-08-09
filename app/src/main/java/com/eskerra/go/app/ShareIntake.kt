package com.eskerra.go.app

import com.eskerra.go.core.share.PendingShare
import com.eskerra.go.core.usecase.FetchSharedPageTitle

/**
 * Everything the shell needs to turn an inbound share into a draft: the share itself (null when
 * none is waiting), the title fetcher, and the acknowledgement that clears it from the Activity
 * so a recomposition cannot re-deliver it.
 */
data class ShareIntake(
    val pendingShare: PendingShare?,
    val fetchSharedPageTitle: FetchSharedPageTitle,
    val onShareHandled: (Long) -> Unit
)
