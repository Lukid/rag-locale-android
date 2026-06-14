package it.netseven.raglocale.modelmanager.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadStateTest {
    @Test
    fun `gli stati conclusivi sono terminali`() {
        assertTrue(DownloadState.Complete(File("/tmp/x")).isTerminal)
        assertTrue(DownloadState.Error("boom").isTerminal)
        assertTrue(DownloadState.Cancelled("annullato").isTerminal)
    }

    @Test
    fun `gli stati di lavoro non sono terminali`() {
        assertFalse(DownloadState.Idle.isTerminal)
        assertFalse(DownloadState.Connecting("u").isTerminal)
        assertFalse(DownloadState.Downloading(1, 2, 50f).isTerminal)
        assertFalse(DownloadState.PartiallyDownloaded(1, 2, 50).isTerminal)
    }

    @Test
    fun `gli stati attivi sono solo quelli in lavorazione`() {
        assertTrue(DownloadState.CheckingAccess("u").isActive)
        assertTrue(DownloadState.Connecting("u").isActive)
        assertTrue(DownloadState.Downloading(1, 2, 50f).isActive)
        assertTrue(DownloadState.Retrying(1, 3, "rete").isActive)

        assertFalse(DownloadState.Idle.isActive)
        assertFalse(DownloadState.PartiallyDownloaded(1, 2, 50).isActive)
        assertFalse(DownloadState.Complete(File("/tmp/x")).isActive)
        assertFalse(DownloadState.Error("boom").isActive)
        assertFalse(DownloadState.Cancelled("annullato").isActive)
    }
}
