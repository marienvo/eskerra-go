package com.eskerra.go.feature.todayhub

import com.eskerra.go.core.repository.NoteflowRepository
import com.eskerra.go.core.todayhub.NoteflowConfig
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import com.eskerra.go.data.notes.FakeNoteRegistryRepository

internal fun fakeNoteflowRepository(registry: FakeNoteRegistryRepository) =
    NoteflowRepository { workspace, files ->
        registry.refresh(workspace, files, null).map { index ->
            index.notes.filter { it.id.value.endsWith("/Today.md") }.map { note ->
                NoteflowConfig(
                    note.id,
                    note.title,
                    listOf("Intro body here."),
                    TodayHubFrontmatter.Settings(columns = listOf("Tasks"))
                )
            }
        }
    }
