package it.netseven.raglocale.modelmanager

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.netseven.raglocale.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestione su disco dei modelli: stato, import (staging M1), selezione attivo, rimozione.
 *
 * **Staging (design D8 / memory reference-anti-vocale):** M1 parte dalla **selezione/import
 * di un `.litertlm` già presente sul device** (file picker / `adb push`). Il download da
 * Hugging Face con progresso+ripresa e l'eventuale OAuth/token sono la rifinitura successiva.
 */
@Singleton
class ModelRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val prefs: PreferencesRepository,
    ) {
        private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

        fun fileFor(model: ModelInfo): File = File(modelsDir, model.fileName)

        fun statusFor(model: ModelInfo): ModelStatus {
            val file = fileFor(model)
            return ModelStatusResolver.resolve(
                fileExists = file.exists(),
                fileSizeBytes = if (file.exists()) file.length() else 0L,
                downloadInProgress = false,
            )
        }

        /** Spazio libero (byte) sulla partizione dello storage interno dell'app. */
        fun freeSpaceBytes(): Long = modelsDir.usableSpace

        fun storageCheck(model: ModelInfo): StorageChecker.Result = StorageChecker.check(freeSpaceBytes(), model.sizeBytes)

        /** Importa un `.litertlm` selezionato dall'utente, copiandolo nello storage interno. */
        suspend fun importFromUri(
            uri: Uri,
            model: ModelInfo,
        ): Result<File> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val dest = fileFor(model)
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Impossibile aprire il file selezionato" }
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    dest
                }
            }

        fun remove(model: ModelInfo): Boolean {
            val file = fileFor(model)
            return file.exists() && file.delete()
        }

        suspend fun setActive(model: ModelInfo) = prefs.setActiveModelId(model.id)

        suspend fun activeModelId(): String? = prefs.activeModelId.first()

        /** File del modello attivo se pronto, altrimenti null. */
        suspend fun activeModelFile(): File? {
            val id = activeModelId() ?: ModelCatalog.default.id
            val model = ModelCatalog.byId(id) ?: return null
            val file = fileFor(model)
            return if (file.exists() && file.length() > 0) file else null
        }

        /**
         * Download in-app (task 5.2/5.5): **rinviato** per scelta di staging.
         * L'M1 usa l'import da file (sopra); il download HF con progresso/ripresa e
         * l'OAuth/token sono la rifinitura successiva (vedi tasks.md §5 e design D8).
         */
        fun isInAppDownloadSupported(): Boolean = false
    }
