package it.netseven.raglocale.modelmanager

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCardStateTest {
    @Test
    fun `download attivo ha la precedenza su tutto`() {
        assertEquals(
            CardStato.IN_DOWNLOAD,
            ModelCardState.stato(ModelStatus.READY, downloadActive = true, hasPartial = true, isActive = true),
        )
    }

    @Test
    fun `pronto e attivo e' ATTIVO`() {
        assertEquals(
            CardStato.ATTIVO,
            ModelCardState.stato(ModelStatus.READY, downloadActive = false, hasPartial = false, isActive = true),
        )
    }

    @Test
    fun `pronto ma non attivo e' PRONTO`() {
        assertEquals(
            CardStato.PRONTO,
            ModelCardState.stato(ModelStatus.READY, downloadActive = false, hasPartial = false, isActive = false),
        )
    }

    @Test
    fun `parziale senza download attivo e' PARZIALE`() {
        assertEquals(
            CardStato.PARZIALE,
            ModelCardState.stato(ModelStatus.NOT_DOWNLOADED, downloadActive = false, hasPartial = true, isActive = false),
        )
    }

    @Test
    fun `niente file e niente parziale e' ASSENTE`() {
        assertEquals(
            CardStato.ASSENTE,
            ModelCardState.stato(ModelStatus.NOT_DOWNLOADED, downloadActive = false, hasPartial = false, isActive = false),
        )
    }
}
