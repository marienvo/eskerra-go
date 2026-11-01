package com.eskerra.go.data.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxContentHashTest {
    @Test
    fun matchesTheSharedUtf8RevisionFixture() {
        assertEquals(
            "4b36ff871e096e2f1a69429480295b741d5830c21c6175d0ec61bf9452055337",
            InboxContentHash.of("# Body\n".toByteArray(Charsets.UTF_8))
        )
    }
}
