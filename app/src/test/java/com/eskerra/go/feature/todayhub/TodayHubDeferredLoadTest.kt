package com.eskerra.go.feature.todayhub

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.ActiveTodayHubStore
import com.eskerra.go.core.usecase.LoadTodayHub
import com.eskerra.go.core.usecase.LoadTodayHubRow
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.todayhub.FakeTodayHubSnapshotStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayHubDeferredLoadTest {
    private val store = object : ActiveTodayHubStore {
        override suspend fun read(): String? = null
        override suspend fun save(noteId: String) = Unit
    }

    @Test
    fun registryScanWaitsUntilDeferredRefresh() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val registry = FakeNoteRegistryRepository.withInboxNotes()
            val content = FakeNoteContentRepository.withContent(
                com.eskerra.go.core.model.NoteId("unused.md"),
                ""
            )
            val vm = TodayHubViewModel(
                WorkspaceConfig("test", "vault", null, "main", 0),
                File("."),
                LoadTodayHub(
                    NoteRegistryCache(registry),
                    content,
                    fakeNoteflowRepository(registry)
                ),
                LoadTodayHubRow(content),
                store,
                FakeTodayHubSnapshotStore()
            )
            advanceUntilIdle()
            assertEquals(0, registry.refreshCount)
            assertEquals(TodayHubUiState.Empty, vm.uiState.value)
            vm.startDeferredRefresh()
            advanceUntilIdle()
            assertEquals(2, registry.refreshCount)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
