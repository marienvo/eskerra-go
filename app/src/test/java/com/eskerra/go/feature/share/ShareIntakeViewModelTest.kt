package com.eskerra.go.feature.share

import com.eskerra.go.core.repository.PageTitleFetcher
import com.eskerra.go.core.share.PendingShare
import com.eskerra.go.core.share.SharePrefill
import com.eskerra.go.core.share.SharedContent
import com.eskerra.go.core.usecase.FetchSharedPageTitle
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareIntakeViewModelTest {

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun subjectShareEmitsOneImmediatePrefillAndNeverFetches() = runTest {
        var fetchCount = 0
        val viewModel = viewModel {
            fetchCount++
            "unused"
        }
        val prefills = collect(viewModel)

        viewModel.offer(share(1L, SharedContent("https://example.com/a", "A page title")))
        advanceUntilIdle()

        assertEquals(1, prefills.size)
        assertEquals(SharePrefill.Stage.Immediate, prefills.single().stage)
        assertEquals("A page title\n\nhttps://example.com/a", prefills.single().text)
        assertEquals(0, fetchCount)
    }

    @Test
    fun bareUrlEmitsImmediateThenTitleUpgrade() = runTest {
        val viewModel = viewModel { "Fetched title" }
        val prefills = collect(viewModel)

        viewModel.offer(share(1L, SharedContent("https://example.com/a")))
        advanceUntilIdle()

        assertEquals(
            listOf(SharePrefill.Stage.Immediate, SharePrefill.Stage.TitleUpgrade),
            prefills.map { it.stage }
        )
        assertEquals("Fetched title\n\nhttps://example.com/a", prefills.last().text)
        assertTrue(prefills.all { it.token == 1L })
    }

    @Test
    fun failedFetchLeavesTheImmediatePrefillStanding() = runTest {
        val viewModel = viewModel { null }
        val prefills = collect(viewModel)

        viewModel.offer(share(1L, SharedContent("https://example.com/a")))
        advanceUntilIdle()

        assertEquals(1, prefills.size)
        assertEquals("\n\nhttps://example.com/a", prefills.single().text)
    }

    @Test
    fun throwingFetcherIsContained() = runTest {
        val viewModel = viewModel { error("network on fire") }
        val prefills = collect(viewModel)

        viewModel.offer(share(1L, SharedContent("https://example.com/a")))
        advanceUntilIdle()

        assertEquals(1, prefills.size)
    }

    @Test
    fun newerShareCancelsTheFetchOfTheOlderOne() = runTest {
        val gate = CompletableDeferred<String?>()
        val viewModel = viewModel { gate.await() }
        val prefills = collect(viewModel)

        viewModel.offer(share(1L, SharedContent("https://example.com/first")))
        viewModel.offer(share(2L, SharedContent("Plain text, no url")))
        gate.complete("Late title")
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), prefills.map { it.token })
        assertTrue(prefills.none { it.stage == SharePrefill.Stage.TitleUpgrade })
    }

    @Test
    fun reofferingTheSameShareIsIgnored() = runTest {
        val viewModel = viewModel { null }
        val prefills = collect(viewModel)
        val share = share(1L, SharedContent("Some text"))

        viewModel.offer(share)
        viewModel.offer(share)
        advanceUntilIdle()

        assertEquals(1, prefills.size)
    }

    @Test
    fun blankShareEmitsNothing() = runTest {
        val viewModel = viewModel { null }
        val prefills = collect(viewModel)

        viewModel.offer(share(1L, SharedContent("   ", "  ")))
        advanceUntilIdle()

        assertTrue(prefills.isEmpty())
    }

    private fun share(id: Long, content: SharedContent) = PendingShare(id, content)

    private fun viewModel(fetch: suspend (String) -> String?): ShareIntakeViewModel =
        ShareIntakeViewModel(FetchSharedPageTitle(PageTitleFetcher { url -> fetch(url) }))

    private fun TestScope.collect(viewModel: ShareIntakeViewModel): List<SharePrefill> {
        val prefills = mutableListOf<SharePrefill>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.prefills.collect { prefills += it }
        }
        return prefills
    }
}
