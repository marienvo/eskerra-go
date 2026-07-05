package com.eskerra.go.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellInputStateTest {

    @Test
    fun noteMode_exposesDraftAndNoteCallbacks() {
        var changedDraft: String? = null
        var saveCount = 0
        var requestedSearchMode: Boolean? = null
        val presentation = buildShellInputPresentation(
            searchMode = false,
            searchQuery = "ignored",
            newNoteInputState = newNoteState(
                draft = "Draft",
                onDraftChange = { changedDraft = it },
                onSave = { saveCount++ }
            ),
            onSearchModeChange = { requestedSearchMode = it },
            onSearchQueryChange = { error("Search callback should not be exposed") },
            onSearchSubmit = { error("Search callback should not be exposed") }
        )

        assertTrue(presentation.visible)
        assertFalse(presentation.searchMode)
        assertEquals("Draft", presentation.value)
        assertTrue(presentation.submitEnabled)
        assertFalse(presentation.isSaving)
        assertEquals("Draft error", presentation.errorMessage)

        presentation.onValueChange("Updated")
        presentation.onSubmit()
        presentation.onSearchModeChange(true)

        assertEquals("Updated", changedDraft)
        assertEquals(1, saveCount)
        assertEquals(true, requestedSearchMode)
    }

    @Test
    fun searchMode_exposesQueryAndSearchCallbacks() {
        var changedQuery: String? = null
        var submitCount = 0
        val presentation = buildShellInputPresentation(
            searchMode = true,
            searchQuery = "meeting notes",
            newNoteInputState = newNoteState(
                onDraftChange = { error("Note callback should not be exposed") },
                onSave = { error("Note callback should not be exposed") }
            ),
            onSearchModeChange = {},
            onSearchQueryChange = { changedQuery = it },
            onSearchSubmit = { submitCount++ }
        )

        assertTrue(presentation.searchMode)
        assertEquals("meeting notes", presentation.value)
        assertTrue(presentation.submitEnabled)
        assertFalse(presentation.isSaving)
        assertNull(presentation.errorMessage)

        presentation.onValueChange("updated query")
        presentation.onSubmit()

        assertEquals("updated query", changedQuery)
        assertEquals(1, submitCount)
    }

    @Test
    fun submitIsDisabled_forBlankSearchOrSavingNote() {
        val blankSearch = buildShellInputPresentation(
            searchMode = true,
            searchQuery = " ",
            newNoteInputState = newNoteState(),
            onSearchModeChange = {},
            onSearchQueryChange = {},
            onSearchSubmit = {}
        )
        val savingNote = buildShellInputPresentation(
            searchMode = false,
            searchQuery = "",
            newNoteInputState = newNoteState(isSaving = true),
            onSearchModeChange = {},
            onSearchQueryChange = {},
            onSearchSubmit = {}
        )

        assertFalse(blankSearch.submitEnabled)
        assertFalse(savingNote.submitEnabled)
    }

    private fun newNoteState(
        draft: String = "",
        isSaving: Boolean = false,
        onDraftChange: (String) -> Unit = {},
        onSave: () -> Unit = {}
    ) = ShellNewNoteInputState(
        visible = true,
        draft = draft,
        canSave = true,
        isSaving = isSaving,
        errorMessage = "Draft error",
        onDraftChange = onDraftChange,
        onSave = onSave
    )
}
