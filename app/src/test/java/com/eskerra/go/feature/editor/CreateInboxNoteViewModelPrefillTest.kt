package com.eskerra.go.feature.editor

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.share.SharePrefill
import com.eskerra.go.core.usecase.CreateInboxNote
import com.eskerra.go.core.usecase.LoadGitStatusSummary
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FakeNoteWriteRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CreateInboxNoteViewModelPrefillTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private val immediate = SharePrefill(
        token = 1L,
        stage = SharePrefill.Stage.Immediate,
        text = "\n\nhttps://example.com/a",
        caretOffset = 0
    )

    private val upgrade = SharePrefill(
        token = 1L,
        stage = SharePrefill.Stage.TitleUpgrade,
        text = "A page title\n\nhttps://example.com/a",
        caretOffset = "A page title".length
    )

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun immediatePrefillSeedsBlankDraftAndReportsCaret() = runTest {
        val viewModel = createViewModel()
        val carets = collectCarets(viewModel)

        viewModel.prefillFromShare(immediate)

        assertEquals(immediate.text, draftOf(viewModel))
        // The title line is still empty, so saving stays disabled until the user types one.
        assertFalse(contentOf(viewModel).canSave)
        assertEquals(listOf(0), carets)
    }

    @Test
    fun immediatePrefillWithTitleEnablesSaving() = runTest {
        val viewModel = createViewModel()

        viewModel.prefillFromShare(immediate.copy(text = "A subject\n\nbody", caretOffset = 9))

        assertTrue(contentOf(viewModel).canSave)
    }

    @Test
    fun immediatePrefillAppendsBelowTypedText() = runTest {
        val viewModel = createViewModel()
        val carets = collectCarets(viewModel)
        viewModel.updateDraft("My own title\n\nMy own body")

        viewModel.prefillFromShare(immediate)

        val draft = draftOf(viewModel)
        assertEquals("My own title\n\nMy own body\n\nhttps://example.com/a", draft)
        assertEquals(listOf(draft.length), carets)
    }

    @Test
    fun titleUpgradeAppliesToAnUntouchedSharedDraft() = runTest {
        val viewModel = createViewModel()
        val carets = collectCarets(viewModel)

        viewModel.prefillFromShare(immediate)
        viewModel.prefillFromShare(upgrade)

        assertEquals(upgrade.text, draftOf(viewModel))
        assertTrue(contentOf(viewModel).canSave)
        assertEquals(listOf(0, upgrade.caretOffset), carets)
    }

    @Test
    fun titleUpgradeIsDroppedAfterTheUserTypes() = runTest {
        val viewModel = createViewModel()

        viewModel.prefillFromShare(immediate)
        viewModel.updateDraft("My own title\n\nhttps://example.com/a")
        viewModel.prefillFromShare(upgrade)

        assertEquals("My own title\n\nhttps://example.com/a", draftOf(viewModel))
    }

    @Test
    fun titleUpgradeIsDroppedWhenTheImmediatePrefillOnlyAppended() = runTest {
        val viewModel = createViewModel()
        viewModel.updateDraft("Something I typed")

        viewModel.prefillFromShare(immediate)
        viewModel.prefillFromShare(upgrade)

        assertEquals("Something I typed\n\nhttps://example.com/a", draftOf(viewModel))
    }

    @Test
    fun titleUpgradeIsDroppedForAStaleToken() = runTest {
        val viewModel = createViewModel()

        viewModel.prefillFromShare(immediate)
        viewModel.prefillFromShare(upgrade.copy(token = 99L))

        assertEquals(immediate.text, draftOf(viewModel))
    }

    @Test
    fun titleUpgradeIsDroppedAfterASave() = runTest {
        val viewModel = savingViewModel()

        viewModel.prefillFromShare(immediate.copy(text = "A title\n\nbody", caretOffset = 7))
        viewModel.save()
        advanceUntilIdle()
        viewModel.prefillFromShare(upgrade)

        assertEquals("", draftOf(viewModel))
    }

    @Test
    fun prefillDuringSaveIsAppliedOnceTheSaveSucceeds() = runTest {
        val viewModel = savingViewModel(writeDelayMs = 1_000L)
        val carets = collectCarets(viewModel)
        viewModel.updateDraft("A title\n\nbody")

        viewModel.save()
        viewModel.prefillFromShare(immediate)
        advanceUntilIdle()

        // The save emptied the draft, so the share takes it over rather than appending.
        assertEquals(immediate.text, draftOf(viewModel))
        assertEquals(listOf(0), carets)
    }

    @Test
    fun prefillDuringAFailedSaveAppendsInsteadOfClobbering() = runTest {
        val viewModel = failingViewModel(writeDelayMs = 1_000L)
        viewModel.updateDraft("A title\n\nbody")

        viewModel.save()
        viewModel.prefillFromShare(immediate)
        advanceUntilIdle()

        assertEquals("A title\n\nbody\n\nhttps://example.com/a", draftOf(viewModel))
    }

    @Test
    fun onlyTheNewestPrefillSurvivesASave() = runTest {
        val viewModel = savingViewModel(writeDelayMs = 1_000L)
        viewModel.updateDraft("A title\n\nbody")

        viewModel.save()
        viewModel.prefillFromShare(immediate)
        viewModel.prefillFromShare(immediate.copy(token = 2L, text = "\n\nsecond share"))
        advanceUntilIdle()

        assertEquals("\n\nsecond share", draftOf(viewModel))
    }

    private fun contentOf(viewModel: CreateInboxNoteViewModel): CreateInboxUiState.Content =
        viewModel.uiState.value as CreateInboxUiState.Content

    private fun draftOf(viewModel: CreateInboxNoteViewModel): String = contentOf(viewModel).draft

    private fun TestScope.collectCarets(viewModel: CreateInboxNoteViewModel): List<Int> {
        val carets = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.prefillAppliedEvents.collect { carets += it }
        }
        return carets
    }

    private fun createViewModel(): CreateInboxNoteViewModel = viewModelWith(
        FakeNoteRegistryRepository.withInboxNotes()
    )

    private fun savingViewModel(writeDelayMs: Long = 0L): CreateInboxNoteViewModel = viewModelWith(
        FakeNoteRegistryRepository.withInboxNotes(
            NoteSummary(
                NoteId("Inbox/A title.md"),
                "A title",
                "",
                isInbox = true,
                lastModifiedEpochMillis = 1L
            )
        ),
        writeDelayMs
    )

    private fun failingViewModel(writeDelayMs: Long = 0L): CreateInboxNoteViewModel =
        viewModelWith(FakeNoteRegistryRepository.failing(), writeDelayMs)

    private fun viewModelWith(
        registry: FakeNoteRegistryRepository,
        writeDelayMs: Long = 0L
    ): CreateInboxNoteViewModel = CreateInboxNoteViewModel(
        config = config,
        filesDir = temp.newFolder(),
        createInboxNote = CreateInboxNote(
            writeRepository = FakeNoteWriteRepository().apply {
                setWriteDelayMs(writeDelayMs)
            },
            registryCache = NoteRegistryCache(registry),
            loadGitStatusSummary = LoadGitStatusSummary(
                JGitWorkspaceRepository(),
                Dispatchers.Main
            )
        )
    )
}
