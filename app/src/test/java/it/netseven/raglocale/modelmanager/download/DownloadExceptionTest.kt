package it.netseven.raglocale.modelmanager.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadExceptionTest {
    @Test
    fun `i codici di successo non sono errori`() {
        assertNull(classifyHttpError(200))
        assertNull(classifyHttpError(206))
    }

    @Test
    fun `401 mappa a Unauthorized`() {
        assertTrue(classifyHttpError(401) is DownloadException.Unauthorized)
    }

    @Test
    fun `403 mappa a Forbidden`() {
        assertTrue(classifyHttpError(403) is DownloadException.Forbidden)
    }

    @Test
    fun `416 mappa a RangeNotSatisfiable`() {
        assertTrue(classifyHttpError(416) is DownloadException.RangeNotSatisfiable)
    }

    @Test
    fun `altri codici mappano a HttpError col loro status`() {
        val e = classifyHttpError(503)
        assertTrue(e is DownloadException.HttpError)
        assertEquals(503, (e as DownloadException.HttpError).statusCode)
    }

    @Test
    fun `solo gli errori di rete sono ritentabili`() {
        assertTrue(DownloadException.NetworkError("timeout").isRetriable)
        assertFalse(DownloadException.Unauthorized("401").isRetriable)
        assertFalse(DownloadException.Forbidden("403").isRetriable)
        assertFalse(DownloadException.RangeNotSatisfiable("416").isRetriable)
        assertFalse(DownloadException.Cancelled("annullato").isRetriable)
        assertFalse(DownloadException.HttpError(500, "boom").isRetriable)
    }

    @Test
    fun `401 e 403 sono legati all'autenticazione`() {
        assertTrue(DownloadException.Unauthorized("401").isAuthRelated)
        assertTrue(DownloadException.Forbidden("403").isAuthRelated)
        assertFalse(DownloadException.NetworkError("timeout").isAuthRelated)
    }
}
