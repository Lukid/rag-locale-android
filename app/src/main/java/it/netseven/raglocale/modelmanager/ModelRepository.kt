package it.netseven.raglocale.modelmanager

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.netseven.raglocale.data.PreferencesRepository
import it.netseven.raglocale.modelmanager.download.AccessResult
import it.netseven.raglocale.modelmanager.download.DownloadConfig
import it.netseven.raglocale.modelmanager.download.DownloadException
import it.netseven.raglocale.modelmanager.download.DownloadHttp
import it.netseven.raglocale.modelmanager.download.DownloadState
import it.netseven.raglocale.modelmanager.download.ModelDownloader
import it.netseven.raglocale.modelmanager.download.RealModelDownloader
import it.netseven.raglocale.modelmanager.download.ResumeDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/** Coordinate su disco dell'embedder attivo e pronto (modello + tokenizer). */
data class EmbedderAttivo(
    val embedderId: String,
    val modelFile: File,
    val tokenizerFile: File,
)

/**
 * Gestione su disco dei modelli (LLM ed embedder): stato, import (staging), selezione
 * dell'attivo per tipo, rimozione.
 *
 * **Staging (design D8):** acquisizione via import di file già presenti sul device
 * (file picker / `adb push`). L'import avviene su file temporaneo `.part`, verifica
 * l'integrità (checksum md5 quando noto — lezione M1: la sola dimensione non basta) e poi
 * sposta atomicamente. Un embedder ha due file (modello + tokenizer): vanno importati
 * entrambi perché risulti pronto.
 */
@Singleton
class ModelRepository(
    private val context: Context,
    private val prefs: PreferencesRepository,
    private val downloader: ModelDownloader,
) {
    // Costruttore usato da Hilt; i test costruiscono il primario con un downloader fake.
    @Inject
    constructor(
        @ApplicationContext context: Context,
        prefs: PreferencesRepository,
        downloader: RealModelDownloader,
    ) : this(context, prefs, downloader as ModelDownloader)

    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    private fun fileFor(fileName: String): File = File(modelsDir, fileName)

    /** File principale del modello. */
    fun fileFor(model: ModelInfo): File = fileFor(model.fileName)

    /** Stato complessivo del modello: per l'embedder considera anche il tokenizer companion. */
    fun statusFor(model: ModelInfo): ModelStatus {
        val primary = fileFor(model.fileName)
        val companion = model.companion?.let { fileFor(it.fileName) }
        return ModelStatusResolver.resolveModel(
            model = model,
            primaryExists = primary.exists(),
            primarySizeBytes = if (primary.exists()) primary.length() else 0L,
            companionExists = companion?.exists() ?: false,
            companionSizeBytes = if (companion?.exists() == true) companion.length() else 0L,
            downloadInProgress = false,
        )
    }

    /** Spazio libero (byte) sulla partizione dello storage interno dell'app. */
    fun freeSpaceBytes(): Long = modelsDir.usableSpace

    fun storageCheck(model: ModelInfo): StorageChecker.Result {
        val totale = model.sizeBytes + (model.companion?.sizeBytes ?: 0L)
        return StorageChecker.check(freeSpaceBytes(), totale)
    }

    /**
     * Importa un singolo file del modello (principale o companion) descritto da [target],
     * copiandolo nello storage interno con staging `.part`, verifica del checksum e move atomico.
     */
    suspend fun importFromUri(
        uri: Uri,
        target: ImportTarget,
        onProgress: (copied: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> =
        withContext(Dispatchers.IO) {
            val part = fileFor("${target.fileName}.part")
            runCatching {
                part.delete()
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Impossibile aprire il file selezionato" }
                    val total = target.sizeBytes
                    var copied = 0L
                    part.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress(copied, total)
                        }
                    }
                }
                val esito =
                    ImportVerifier.verifica(
                        fileSizeBytes = part.length(),
                        expectedSizeBytes = target.sizeBytes,
                        computedMd5 = if (target.expectedMd5 != null) FileChecksum.md5(part) else null,
                        expectedMd5 = target.expectedMd5,
                    )
                if (esito is ImportVerifier.Esito.Rifiutato) error(esito.motivo)
                val dest = fileFor(target.fileName)
                moveIntoPlace(part, dest)
                dest
            }.onFailure { part.delete() }
        }

    /** Rimuove tutti i file del modello (principale + companion) e gli eventuali `.part`. */
    fun remove(model: ModelInfo): Boolean {
        var rimosso = false
        for (target in model.targets()) {
            fileFor("${target.fileName}.part").delete()
            val file = fileFor(target.fileName)
            if (file.exists() && file.delete()) rimosso = true
        }
        return rimosso
    }

    /** Imposta il modello attivo per il suo tipo (LLM per la chat, embedder per il RAG). */
    suspend fun setActive(model: ModelInfo) =
        when (model.type) {
            ModelType.LLM -> prefs.setActiveModelId(model.id)
            ModelType.EMBEDDER -> prefs.setActiveEmbedderId(model.id)
        }

    suspend fun activeModelId(): String? = prefs.activeModelId.first()

    suspend fun activeEmbedderId(): String =
        prefs.activeEmbedderId.first() ?: ModelCatalog.defaultFor(ModelType.EMBEDDER).id

    /** File del LLM attivo se pronto, altrimenti null. */
    suspend fun activeModelFile(): File? {
        val id = activeModelId() ?: ModelCatalog.defaultFor(ModelType.LLM).id
        val model = ModelCatalog.byId(id)?.takeIf { it.type == ModelType.LLM } ?: return null
        return fileFor(model).takeIf { statusFor(model) == ModelStatus.READY }
    }

    /**
     * File dell'embedder attivo (modello + tokenizer) se pronto, altrimenti null. L'id
     * restituito è quello del modello: viene registrato come metadato dell'indice per il
     * controllo di coerenza ([it.netseven.raglocale.store.CoerenzaEmbedder]).
     */
    suspend fun activeEmbedderFiles(): EmbedderAttivo? {
        val model = ModelCatalog.byId(activeEmbedderId())?.takeIf { it.type == ModelType.EMBEDDER } ?: return null
        if (statusFor(model) != ModelStatus.READY) return null
        val companion = model.companion ?: return null
        return EmbedderAttivo(
            embedderId = model.id,
            modelFile = fileFor(model),
            tokenizerFile = fileFor(companion.fileName),
        )
    }

    /** True se il modello è scaricabile in-app (ha gli URL per tutti i suoi file). */
    fun isInAppDownloadSupported(model: ModelInfo): Boolean = model.scaricabile

    /**
     * Scarica in-app tutti i file del [model] (principale + eventuale companion), riusando lo
     * **stesso staging dell'import**: download su `.part`, verifica del checksum md5 esatto e
     * **move atomico**. Strategia public-first: il file pubblico scarica senza token; quello
     * gated usa [getToken] (se assente → errore di auth, senza lasciare parziali nell'indice).
     * Nessun file diventa pronto/attivo finché non è scaricato **e** verificato.
     */
    suspend fun download(
        model: ModelInfo,
        getToken: suspend () -> String? = { null },
        onState: (DownloadState) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(model.scaricabile) { "Modello non scaricabile in-app" }
                if (statusFor(model) == ModelStatus.READY) return@runCatching
                val storage = storageCheck(model)
                if (!storage.sufficient) {
                    error(
                        "Spazio insufficiente: liberi ${storage.freeBytes} byte, servono " +
                            "~${storage.requiredBytes} (mancano ${storage.missingBytes}).",
                    )
                }
                for (target in model.targets()) {
                    val finalFile = fileFor(target.fileName)
                    if (finalFile.exists() && verificaFile(finalFile, target) is ImportVerifier.Esito.Ok) continue
                    scaricaFile(target, getToken, onState, isCancelled).getOrThrow()
                }
            }
        }

    private suspend fun scaricaFile(
        target: ImportTarget,
        getToken: suspend () -> String?,
        onState: (DownloadState) -> Unit,
        isCancelled: () -> Boolean,
    ): Result<File> {
        val url = target.downloadUrl ?: return Result.failure(IllegalStateException("URL di download assente per ${target.fileName}"))
        val part = fileFor("${target.fileName}.part")
        return runCatching {
            onState(DownloadState.CheckingAccess(url))
            val authHeader =
                when (val access = downloader.checkAccess(url, authHeader = null)) {
                    is AccessResult.Public -> null
                    AccessResult.NeedsAuth -> {
                        val token =
                            getToken() ?: throw DownloadException.Unauthorized(
                                "Questo modello richiede l'accesso a HuggingFace: accedi per scaricarlo.",
                            )
                        DownloadHttp.authHeader(token)
                    }
                    is AccessResult.Failure -> throw access.error
                }
            val config =
                DownloadConfig(
                    url = url,
                    // Scarichiamo nel .part senza rename: verifichiamo lì e poi facciamo il move atomico.
                    tempFile = part,
                    targetFile = part,
                    estimatedSizeBytes = target.sizeBytes,
                    authHeader = authHeader,
                    isCancelled = isCancelled,
                )
            val downloaded = downloader.download(config, onState).getOrThrow()
            val esito = verificaFile(downloaded, target)
            if (esito is ImportVerifier.Esito.Rifiutato) {
                ResumeDownloadHelper.clearPartial(part)
                error(esito.motivo)
            }
            val dest = fileFor(target.fileName)
            moveIntoPlace(downloaded, dest)
            onState(DownloadState.Complete(dest))
            dest
        }.onFailure { e ->
            // Su rete/annullamento il .part resta per la ripresa; su corruzione è già stato pulito.
            onState(DownloadState.Error(e.message ?: "Download fallito", e))
        }
    }

    private fun verificaFile(
        file: File,
        target: ImportTarget,
    ): ImportVerifier.Esito =
        ImportVerifier.verifica(
            fileSizeBytes = file.length(),
            expectedSizeBytes = target.sizeBytes,
            computedMd5 = if (target.expectedMd5 != null) FileChecksum.md5(file) else null,
            expectedMd5 = target.expectedMd5,
        )

    /** Esiste un download parziale (`.part`) per uno dei file del modello? (ripresa disponibile). */
    fun hasPartial(model: ModelInfo): Boolean =
        model.targets().any { fileFor("${it.fileName}.part").length() > 0L }

    /** Elimina i parziali (`.part` + sidecar `.size`) del modello. */
    fun clearPartial(model: ModelInfo) {
        for (target in model.targets()) {
            ResumeDownloadHelper.clearPartial(fileFor("${target.fileName}.part"))
        }
    }

    private fun moveIntoPlace(
        source: File,
        dest: File,
    ) {
        try {
            Files.move(
                source.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
