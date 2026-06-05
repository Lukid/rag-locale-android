package it.netseven.raglocale.modelmanager

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelStatusResolverTest {
    @Test
    fun `download in corso ha precedenza`() {
        assertEquals(
            ModelStatus.DOWNLOADING,
            ModelStatusResolver.resolve(fileExists = true, fileSizeBytes = 10, downloadInProgress = true),
        )
    }

    @Test
    fun `file presente sopra soglia minima e' pronto`() {
        assertEquals(
            ModelStatus.READY,
            ModelStatusResolver.resolve(
                fileExists = true,
                fileSizeBytes = ModelStatusResolver.MIN_READY_FILE_BYTES,
                downloadInProgress = false,
            ),
        )
    }

    @Test
    fun `file assente e' non scaricato`() {
        assertEquals(
            ModelStatus.NOT_DOWNLOADED,
            ModelStatusResolver.resolve(fileExists = false, fileSizeBytes = 0, downloadInProgress = false),
        )
    }

    @Test
    fun `file presente ma vuoto e' non scaricato`() {
        assertEquals(
            ModelStatus.NOT_DOWNLOADED,
            ModelStatusResolver.resolve(fileExists = true, fileSizeBytes = 0, downloadInProgress = false),
        )
    }

    @Test
    fun `file piccolo non basta per essere pronto`() {
        assertEquals(
            ModelStatus.NOT_DOWNLOADED,
            ModelStatusResolver.resolve(fileExists = true, fileSizeBytes = 10, downloadInProgress = false),
        )
    }

    @Test
    fun `dimensione attesa accetta file entro tolleranza`() {
        assertEquals(
            ModelStatus.READY,
            ModelStatusResolver.resolve(
                fileExists = true,
                fileSizeBytes = 700,
                downloadInProgress = false,
                expectedSizeBytes = 1_000,
            ),
        )
    }

    @Test
    fun `dimensione attesa rifiuta file troppo parziale`() {
        assertEquals(
            ModelStatus.NOT_DOWNLOADED,
            ModelStatusResolver.resolve(
                fileExists = true,
                fileSizeBytes = 699,
                downloadInProgress = false,
                expectedSizeBytes = 1_000,
            ),
        )
    }
}
