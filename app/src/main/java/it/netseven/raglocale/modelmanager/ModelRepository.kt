package it.netseven.raglocale.modelmanager

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.netseven.raglocale.data.PreferencesRepository
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
class ModelRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val prefs: PreferencesRepository,
    ) {
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
        ): Result<File> =
            withContext(Dispatchers.IO) {
                val part = fileFor("${target.fileName}.part")
                runCatching {
                    part.delete()
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Impossibile aprire il file selezionato" }
                        part.outputStream().use { output -> input.copyTo(output) }
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

        /**
         * Download in-app: **rinviato** per scelta di staging (vedi tasks.md §5 e design D8).
         * L'acquisizione avviene via import da file (sopra).
         */
        fun isInAppDownloadSupported(): Boolean = false

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
