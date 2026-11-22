package com.eskerra.go.app

data class NotificationOpenRequest(val noteIds: List<String>, val requestId: Long = 0) {
    companion object {
        fun next(noteIds: List<String>, previous: NotificationOpenRequest) =
            NotificationOpenRequest(noteIds, previous.requestId + 1)
    }
}
