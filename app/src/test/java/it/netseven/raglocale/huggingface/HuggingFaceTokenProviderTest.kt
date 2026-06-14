package it.netseven.raglocale.huggingface

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuggingFaceTokenProviderTest {
    /** Refresher finto: registra le chiamate e ritorna un esito configurato. */
    private class FakeRefresher(
        private val result: RefreshResult,
    ) : TokenRefresher {
        var calls = 0
            private set

        override suspend fun refresh(refreshToken: String): RefreshResult {
            calls++
            return result
        }
    }

    private val now = 1_000_000L
    private val hour = 60 * 60 * 1000L

    @Test
    fun `riavvio loggato con token valido ritorna il token senza rinnovo`() =
        runTest {
            val tm = FakeHuggingFaceTokenManager()
            tm.saveTokens(accessToken = "access1", refreshToken = "refresh1", expiresAtMs = now + hour, username = "luca")
            val refresher = FakeRefresher(RefreshResult.Failure("non dovrebbe servire"))
            val provider = HuggingFaceTokenProvider(tm, refresher, now = { now })

            assertEquals("access1", provider.getEffectiveToken())
            assertEquals("Nessun rinnovo se il token è valido", 0, refresher.calls)
        }

    @Test
    fun `token in scadenza viene rinnovato e persistito`() =
        runTest {
            val tm = FakeHuggingFaceTokenManager()
            // Scaduto: needsRefresh vero.
            tm.saveTokens(accessToken = "old", refreshToken = "refresh1", expiresAtMs = now, username = "luca")
            val refresher = FakeRefresher(RefreshResult.Success("new", "refresh2", now + hour))
            val provider = HuggingFaceTokenProvider(tm, refresher, now = { now })

            assertEquals("new", provider.getEffectiveToken())
            assertEquals(1, refresher.calls)
            assertEquals("Il nuovo token è persistito", "new", tm.accessToken())
            assertEquals(now + hour, tm.expiresAtMs())
        }

    @Test
    fun `rinnovo fallito su token scaduto ritorna null`() =
        runTest {
            val tm = FakeHuggingFaceTokenManager()
            tm.saveTokens(accessToken = "old", refreshToken = "refresh1", expiresAtMs = now - 1, username = "luca")
            val refresher = FakeRefresher(RefreshResult.Failure("server giù"))
            val provider = HuggingFaceTokenProvider(tm, refresher, now = { now })

            assertNull(provider.getEffectiveToken())
            assertEquals(1, refresher.calls)
        }

    @Test
    fun `dopo il logout non c'e' token effettivo`() =
        runTest {
            val tm = FakeHuggingFaceTokenManager()
            tm.saveTokens(accessToken = "access1", refreshToken = "refresh1", expiresAtMs = now + hour, username = "luca")
            tm.logout()
            val refresher = FakeRefresher(RefreshResult.Failure("x"))
            val provider = HuggingFaceTokenProvider(tm, refresher, now = { now })

            assertNull(provider.getEffectiveToken())
            assertEquals(0, refresher.calls)
        }

    @Test
    fun `in scadenza ma senza refresh token usa il token finche' non e' scaduto`() =
        runTest {
            val tm = FakeHuggingFaceTokenManager()
            // Entro il buffer di refresh ma non ancora scaduto, e nessun refresh token.
            tm.saveTokens(accessToken = "access1", refreshToken = "", expiresAtMs = now + 60_000, username = "luca")
            val refresher = FakeRefresher(RefreshResult.Failure("x"))
            val provider = HuggingFaceTokenProvider(tm, refresher, now = { now })

            assertEquals("access1", provider.getEffectiveToken())
            assertEquals("Senza refresh token non si tenta il rinnovo", 0, refresher.calls)
        }
}
