package it.netseven.raglocale.modelmanager

import it.netseven.raglocale.data.PreferencesRepository
import it.netseven.raglocale.modelmanager.download.AccessResult
import it.netseven.raglocale.modelmanager.download.DownloadConfig
import it.netseven.raglocale.modelmanager.download.DownloadException
import it.netseven.raglocale.modelmanager.download.FakeModelDownloader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.security.MessageDigest

/**
 * Orchestrazione del download in-app (task 6.4) su `ModelRepository` reale via Robolectric, con un
 * [FakeModelDownloader] al posto della rete: download+verifica+move atomico, scarto su md5 errato,
 * ripresa dal `.part`, gated senza token. Fixture sintetiche (file piccoli, md5 sul contenuto).
 */
@RunWith(RobolectricTestRunner::class)
class ModelRepositoryDownloadTest {
    private val context = RuntimeEnvironment.getApplication()

    private val modelloBytes = "contenuto-modello-scaricato".toByteArray()
    private val tokenizerBytes = "contenuto-tokenizer-scaricato".toByteArray()

    /** Embedder sintetico **scaricabile** (URL su modello e companion), con checksum noti. */
    private val model =
        ModelInfo(
            id = "emb-dl",
            displayName = "Embedder scaricabile",
            type = ModelType.EMBEDDER,
            repo = "org/repo",
            fileName = "modello.tflite",
            sizeBytes = modelloBytes.size.toLong(),
            quantization = "int4",
            isDefault = true,
            expectedMd5 = md5(modelloBytes),
            downloadUrl = "https://example.test/modello.tflite",
            gated = true,
            companion =
                CompanionArtifact(
                    fileName = "tokenizer.model",
                    sizeBytes = tokenizerBytes.size.toLong(),
                    expectedMd5 = md5(tokenizerBytes),
                    downloadUrl = "https://example.test/tokenizer.model",
                ),
        )

    private val bytesPerFile =
        mapOf(
            "modello.tflite" to modelloBytes,
            "tokenizer.model" to tokenizerBytes,
        )

    private lateinit var prefs: PreferencesRepository

    @Before
    fun setUp() {
        prefs = PreferencesRepository(context)
    }

    @After
    fun tearDown() {
        // Pulisce file e parziali tra i test.
        ModelRepository(context, prefs, FakeModelDownloader()).apply {
            remove(model)
            clearPartial(model)
        }
    }

    private fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Nome del file finale a partire dal `.part` configurato nel download. */
    private fun fileNameOf(config: DownloadConfig): String = config.tempFile.name.removeSuffix(".part")

    /** Esito che scrive il contenuto corretto del file nel `.part`. */
    private fun scriviCorretto(config: DownloadConfig): Result<File> {
        val bytes = bytesPerFile.getValue(fileNameOf(config))
        config.tempFile.writeBytes(bytes)
        return Result.success(config.tempFile)
    }

    @Test
    fun `download pubblico a buon fine verifica e rende il modello pronto`() =
        runBlocking {
            val downloader = FakeModelDownloader(access = AccessResult.Public(0L, true), onDownload = ::scriviCorretto)
            val repo = ModelRepository(context, prefs, downloader)

            val esito = repo.download(model)

            assertTrue("Il download deve riuscire", esito.isSuccess)
            assertEquals(ModelStatus.READY, repo.statusFor(model))
            assertEquals("Scaricati modello + tokenizer", 2, downloader.downloadConfigs.size)
            assertFalse("Niente .part residuo", repo.hasPartial(model))
        }

    @Test
    fun `download con md5 errato scarta il file e non lascia parziali`() =
        runBlocking {
            val downloader =
                FakeModelDownloader(access = AccessResult.Public(0L, true)) { config ->
                    // Stessa dimensione attesa ma contenuto diverso → md5 diverso (lezione M1).
                    val corrotto = ByteArray(bytesPerFile.getValue(fileNameOf(config)).size) { 'x'.code.toByte() }
                    config.tempFile.writeBytes(corrotto)
                    Result.success(config.tempFile)
                }
            val repo = ModelRepository(context, prefs, downloader)

            val esito = repo.download(model)

            assertTrue("Il download corrotto deve fallire", esito.isFailure)
            assertFalse("Il file corrotto non resta", repo.fileFor(model).exists())
            assertFalse("Nessun .part residuo dopo lo scarto", repo.hasPartial(model))
            assertEquals(ModelStatus.NOT_DOWNLOADED, repo.statusFor(model))
        }

    @Test
    fun `il download riprende dal part esistente invece di ricominciare`() =
        runBlocking {
            // Simula un parziale già su disco per il file principale.
            val part = File(File(context.filesDir, "models").apply { mkdirs() }, "modello.tflite.part")
            part.writeBytes("parziale".toByteArray())

            var risumtoConParziale = false
            val downloader =
                FakeModelDownloader(access = AccessResult.Public(0L, true)) { config ->
                    if (fileNameOf(config) == "modello.tflite" && config.tempFile.length() > 0L) {
                        risumtoConParziale = true
                    }
                    scriviCorretto(config)
                }
            val repo = ModelRepository(context, prefs, downloader)

            val esito = repo.download(model)

            assertTrue(esito.isSuccess)
            assertTrue("Il motore ha ricevuto il .part esistente (ripresa, non da zero)", risumtoConParziale)
            assertEquals(ModelStatus.READY, repo.statusFor(model))
        }

    @Test
    fun `modello gated senza token fallisce con errore di auth e non lascia parziali`() =
        runBlocking {
            val downloader = FakeModelDownloader(access = AccessResult.NeedsAuth)
            val repo = ModelRepository(context, prefs, downloader)

            val esito = repo.download(model, getToken = { null })

            assertTrue(esito.isFailure)
            assertTrue(
                "L'errore è di autenticazione",
                esito.exceptionOrNull() is DownloadException.Unauthorized,
            )
            assertTrue("Il download non è stato avviato", downloader.downloadConfigs.isEmpty())
            assertFalse(repo.fileFor(model).exists())
            assertEquals(ModelStatus.NOT_DOWNLOADED, repo.statusFor(model))
        }

    @Test
    fun `modello gated con token usa l'header di autorizzazione`() =
        runBlocking {
            var headerVisto: String? = null
            val downloader =
                FakeModelDownloader(access = AccessResult.NeedsAuth) { config ->
                    headerVisto = config.authHeader
                    scriviCorretto(config)
                }
            val repo = ModelRepository(context, prefs, downloader)

            val esito = repo.download(model, getToken = { "hf_token_123" })

            assertTrue(esito.isSuccess)
            assertEquals("Bearer hf_token_123", headerVisto)
            assertEquals(ModelStatus.READY, repo.statusFor(model))
        }
}
