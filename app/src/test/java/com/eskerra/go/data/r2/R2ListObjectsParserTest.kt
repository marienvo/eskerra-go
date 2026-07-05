package com.eskerra.go.data.r2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2ListObjectsParserTest {

    @Test
    fun `parses contents with key size and unquoted etag`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ListBucketResult>
              <IsTruncated>false</IsTruncated>
              <Contents>
                <Key>binaries/Assets/a.pdf</Key>
                <Size>1234</Size>
                <ETag>&quot;abc123&quot;</ETag>
              </Contents>
              <Contents>
                <Key>binaries/b.bin</Key>
                <Size>10</Size>
                <ETag>"def"</ETag>
              </Contents>
            </ListBucketResult>
        """.trimIndent()

        val page = R2ListObjectsParser.parse(xml)

        assertEquals(2, page.objects.size)
        assertEquals("binaries/Assets/a.pdf", page.objects[0].key)
        assertEquals(1234L, page.objects[0].size)
        assertEquals("abc123", page.objects[0].etag)
        assertEquals("def", page.objects[1].etag)
        assertFalse(page.isTruncated)
        assertNull(page.nextContinuationToken)
    }

    @Test
    fun `reports truncation and continuation token`() {
        val xml = """
            <ListBucketResult>
              <IsTruncated>true</IsTruncated>
              <NextContinuationToken>tok-42</NextContinuationToken>
              <Contents><Key>binaries/x</Key><Size>1</Size><ETag>"e"</ETag></Contents>
            </ListBucketResult>
        """.trimIndent()

        val page = R2ListObjectsParser.parse(xml)

        assertTrue(page.isTruncated)
        assertEquals("tok-42", page.nextContinuationToken)
    }

    @Test
    fun `empty listing yields no objects`() {
        val page = R2ListObjectsParser.parse(
            "<ListBucketResult><IsTruncated>false</IsTruncated></ListBucketResult>"
        )
        assertTrue(page.objects.isEmpty())
    }
}
