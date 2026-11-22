package com.eskerra.go.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.notifications.DatedReminderHandled
import com.eskerra.go.core.notifications.DatedReminderSettings
import com.eskerra.go.core.notifications.DesktopActivityStatus
import com.eskerra.go.core.notifications.NotificationEvent
import com.eskerra.go.core.notifications.NotificationEventType
import com.eskerra.go.core.repository.DatedReminderSettingsRepository
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.data.git.GitSyncMutex
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

internal class Fixture(
    markdown: Map<String, String>,
    private val activity: Pair<DesktopActivityStatus, Long?> =
        DesktopActivityStatus.Inactive to null,
    private val deliverResult: Boolean = true,
    private val onDesktopCheck: () -> Unit = {},
    private val currentTime: () -> Long = { NOW },
    private val settingsSequence: List<DatedReminderSettings> = emptyList(),
    private val workspaceCurrent: () -> Boolean = { true }
) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val config = WorkspaceConfig(
        "vault",
        "vault",
        "https://example.com",
        "main",
        UUID.randomUUID().mostSignificantBits
    )
    val workspace = notificationWorkspaceKey(config)
    val scheduler = FakeScheduler()
    val deliveries = mutableListOf<List<NotificationEvent>>()
    val deliveriesInsideMutex = mutableListOf<Boolean>()
    val reads = mutableListOf<String>()
    val desktopChecksOutsideMutex = mutableListOf<Boolean>()
    val noteReadsInsideMutex = mutableListOf<Boolean>()
    private val mutex = GitSyncMutex()
    private val notes = object : NoteContentRepository {
        override suspend fun load(
            config: WorkspaceConfig,
            filesDir: File,
            noteId: NoteId
        ): Result<NoteContent> {
            reads += noteId.value
            noteReadsInsideMutex += mutex.mutex.isLocked
            return Result.success(
                NoteContent(
                    noteId,
                    NotePath.fromRelativePath(noteId.value).getOrThrow(),
                    markdown.getValue(noteId.value)
                )
            )
        }
    }
    var repetitionEnabled = true
    private var settingsReads = 0
    private val settings = object : DatedReminderSettingsRepository {
        override fun load(): DatedReminderSettings {
            if (settingsSequence.isNotEmpty()) {
                val index = settingsReads++.coerceAtMost(settingsSequence.lastIndex)
                return settingsSequence[index]
            }
            return DatedReminderSettings(enabled = repetitionEnabled)
        }
        override fun save(settings: DatedReminderSettings) = Result.success(Unit)
    }
    val coordinator = newCoordinator()

    init {
        context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", true).commit()
    }

    fun newCoordinator() = NotificationAlarmCoordinator(
        context = context,
        filesDir = File("."),
        loadDesktopActivity = { _, _ ->
            desktopChecksOutsideMutex += !mutex.mutex.isLocked
            onDesktopCheck()
            activity
        },
        scheduler = scheduler,
        gitSyncMutex = mutex,
        notes = notes,
        reminderSettings = settings,
        delivery = { events ->
            deliveries += events
            deliveriesInsideMutex += mutex.mutex.isLocked
            deliverResult
        },
        isCurrentWorkspace = { workspaceCurrent() },
        clock = currentTime,
        zone = { ZONE }
    )

    fun seed(events: List<NotificationEvent>) {
        NotificationSnapshotStore(context).use {
            it.replaceSnapshot(workspace, events, FIRST_SEEN)
        }
    }

    fun handled() = NotificationSnapshotStore(context).use {
        it.datedReminderHandled(workspace)
    }

    fun pending() = NotificationSnapshotStore(context).use { it.pending(workspace) }

    fun handledKey(noteId: String) =
        com.eskerra.go.core.notifications.DatedReminderHandled(noteId, "2026-11-22")
}

internal class FakeScheduler : NotificationAlarmScheduling {
    var next: Long? = null
    override fun schedule(atEpochMillis: Long) {
        next = atEpochMillis
    }

    override fun cancel() {
        next = null
    }
}

val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
val NOW = at("2026-11-22T08:00:00")
val FIRST_SEEN = at("2026-11-01T08:00:00")
const val OLDER = "Flow/Work/2026-11-21.md"
const val NEWEST = "Flow/Work/2026-11-22.md"

fun at(value: String): Long = LocalDateTime.parse(value).atZone(ZONE)
    .toInstant().toEpochMilli()

fun event(
    noteId: String,
    due: Long = 0,
    type: NotificationEventType = NotificationEventType.DATED_NOTE
) = NotificationEvent("dated:$noteId", noteId, type, due, "Reminder")
