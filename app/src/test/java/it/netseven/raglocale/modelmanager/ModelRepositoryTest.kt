package it.netseven.raglocale.modelmanager

import android.net.Uri
import it.netseven.raglocale.data.PreferencesRepository
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
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/**
 * Import generalizzato (task 5.3) e selezione attivo per tipo (task 5.2) su `ModelRepository`
 * reale via Robolectric — niente device. Le fixture sono sintetiche (file piccoli, md5
 * calcolato sul contenuto) così l'import si verifica senza i 196 MB del modello reale.
 */
@RunWith(RobolectricTestRunner::class)
class ModelRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var repository: ModelRepository

    private val modelloBytes = "contenuto-modello-embedder".toByteArray()
    private val tokenizerBytes = "contenuto-tokenizer".toByteArray()

    /** Embedder sintetico a due file con checksum noti, modellato come EmbeddingGemma. */
    private val embedder =
        ModelInfo(
            id = "emb-test",
            displayName = "Embedder di test",
            type = ModelType.EMBEDDER,
            repo = "",
            fileName = "modello.tflite",
            sizeBytes = modelloBytes.size.toLong(),
            quantization = "int4",
            isDefault = true,
            expectedMd5 = md5(modelloBytes),
            companion =
                CompanionArtifact(
                    fileName = "tokenizer.model",
                    sizeBytes = tokenizerBytes.size.toLong(),
                    expectedMd5 = md5(tokenizerBytes),
                ),
        )

    @Before
    fun setUp() {
        repository = ModelRepository(context, PreferencesRepository(context))
    }

    @After
    fun tearDown() {
        repository.remove(embedder)
    }

    private fun uriCon(
        nome: String,
        bytes: ByteArray,
    ): Uri {
        val uri = Uri.parse("content://test/$nome")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return uri
    }

    @Test
    fun `import di modello e tokenizer rende l'embedder pronto`() =
        runBlocking {
            val targetModello = embedder.targets()[0]
            val targetTokenizer = embedder.targets()[1]

            assertTrue(repository.importFromUri(uriCon("m", modelloBytes), targetModello).isSuccess)
            // Con il solo modello, senza tokenizer, non è ancora pronto (macchina a stati per tipo).
            assertEquals(ModelStatus.NOT_DOWNLOADED, repository.statusFor(embedder))

            assertTrue(repository.importFromUri(uriCon("t", tokenizerBytes), targetTokenizer).isSuccess)
            assertEquals(ModelStatus.READY, repository.statusFor(embedder))
        }

    @Test
    fun `import con checksum non corrispondente viene scartato e non lascia il file`() =
        runBlocking {
            val targetModello = embedder.targets()[0]
            // Contenuto diverso → stessa dimensione attesa ma md5 diverso (lezione M1).
            val corrotto = "contenuto-modello-XXXXXXXX".toByteArray()
            assertEquals(modelloBytes.size, corrotto.size)

            val esito = repository.importFromUri(uriCon("c", corrotto), targetModello)

            assertTrue("L'import corrotto deve fallire", esito.isFailure)
            assertFalse("Il file scartato non deve restare su disco", repository.fileFor(embedder).exists())
            assertEquals(ModelStatus.NOT_DOWNLOADED, repository.statusFor(embedder))
        }

    @Test
    fun `rimozione cancella sia il modello sia il tokenizer`() =
        runBlocking {
            repository.importFromUri(uriCon("m", modelloBytes), embedder.targets()[0])
            repository.importFromUri(uriCon("t", tokenizerBytes), embedder.targets()[1])
            assertEquals(ModelStatus.READY, repository.statusFor(embedder))

            repository.remove(embedder)

            assertEquals(ModelStatus.NOT_DOWNLOADED, repository.statusFor(embedder))
        }

    @Test
    fun `la selezione dell'attivo distingue LLM ed embedder`() =
        runBlocking {
            repository.setActive(ModelCatalog.GEMMA_4_E2B)
            repository.setActive(ModelCatalog.EMBEDDING_GEMMA)

            assertEquals("gemma-4-e2b-it", repository.activeModelId())
            assertEquals("embeddinggemma-300m", repository.activeEmbedderId())
        }

    private fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
}
