package it.netseven.raglocale.modelmanager.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadHttpTest {
    // --- Header Range ---

    @Test
    fun `rangeHeader e' nullo quando non c'e' nulla da riprendere`() {
        assertNull(DownloadHttp.rangeHeader(0L))
        assertNull(DownloadHttp.rangeHeader(-5L))
    }

    @Test
    fun `rangeHeader riprende dal byte parziale`() {
        assertEquals("bytes=1024-", DownloadHttp.rangeHeader(1024L))
    }

    // --- Header Authorization ---

    @Test
    fun `authHeader e' nullo per download anonimo`() {
        assertNull(DownloadHttp.authHeader(null))
        assertNull(DownloadHttp.authHeader(""))
        assertNull(DownloadHttp.authHeader("   "))
    }

    @Test
    fun `authHeader e' Bearer quando il token serve`() {
        assertEquals("Bearer hf_abc123", DownloadHttp.authHeader("hf_abc123"))
    }

    // --- Parsing Content-Range ---

    @Test
    fun `parseContentRange estrae start end e totale`() {
        val r = DownloadHttp.parseContentRange("bytes 12345-67890/67891")
        assertEquals(ContentRange(12345L, 67890L, 67891L), r)
    }

    @Test
    fun `parseContentRange tollera il totale sconosciuto`() {
        val r = DownloadHttp.parseContentRange("bytes 0-99/*")
        assertEquals(ContentRange(0L, 99L, null), r)
    }

    @Test
    fun `parseContentRange e' nullo per header assente o malformato`() {
        assertNull(DownloadHttp.parseContentRange(null))
        assertNull(DownloadHttp.parseContentRange(""))
        assertNull(DownloadHttp.parseContentRange("garbage"))
        assertNull(DownloadHttp.parseContentRange("bytes 100/200"))
    }

    // --- Decisione 200 vs 206 ---

    @Test
    fun `200 riparte da zero con il Content-Length`() {
        val d =
            DownloadHttp.decideResume(
                200,
                partialSize = 500L,
                contentRangeHeader = null,
                contentLength = 2000L,
                estimatedSizeBytes = 9L,
            )
        assertEquals(ResumeDecision.StartFresh(2000L), d)
    }

    @Test
    fun `200 ripiega sulla stima se manca il Content-Length`() {
        val d =
            DownloadHttp.decideResume(
                200,
                partialSize = 500L,
                contentRangeHeader = null,
                contentLength = 0L,
                estimatedSizeBytes = 4242L,
            )
        assertEquals(ResumeDecision.StartFresh(4242L), d)
    }

    @Test
    fun `206 riprende dall'offset e dal totale del Content-Range`() {
        val d =
            DownloadHttp.decideResume(
                206,
                partialSize = 500L,
                contentRangeHeader = "bytes 500-1999/2000",
                contentLength = 1500L,
                estimatedSizeBytes = 9L,
            )
        assertEquals(ResumeDecision.Resume(500L, 2000L), d)
    }

    @Test
    fun `206 senza Content-Range usa la dimensione parziale e la stima`() {
        val d =
            DownloadHttp.decideResume(
                206,
                partialSize = 777L,
                contentRangeHeader = null,
                contentLength = 0L,
                estimatedSizeBytes = 5000L,
            )
        assertEquals(ResumeDecision.Resume(777L, 5000L), d)
    }

    @Test
    fun `i codici non di successo non producono una decisione di ripresa`() {
        assertNull(DownloadHttp.decideResume(401, 0L, null, 0L, 9L))
        assertNull(DownloadHttp.decideResume(403, 0L, null, 0L, 9L))
        assertNull(DownloadHttp.decideResume(416, 0L, null, 0L, 9L))
        assertNull(DownloadHttp.decideResume(500, 0L, null, 0L, 9L))
    }
}
