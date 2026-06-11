package it.netseven.raglocale.retrieval

import it.netseven.raglocale.modelmanager.ModelRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fornisce e tiene vivo l'[Embedder] reale corrispondente all'embedder attivo nel
 * [ModelRepository]. Il runtime nativo ([GemmaEmbedder]) è costoso da inizializzare: lo si
 * costruisce una volta e lo si tiene residente (design M2 D7), ricreandolo solo quando
 * l'embedder attivo cambia. Se l'embedder attivo non è pronto (file non importati),
 * restituisce `null`.
 */
@Singleton
class EmbedderProvider
    @Inject
    constructor(
        private val modelRepository: ModelRepository,
    ) : FornitoreEmbedder {
        private val mutex = Mutex()
        private var cache: GemmaEmbedder? = null

        override suspend fun embedder(): Embedder? =
            mutex.withLock {
                val files = modelRepository.activeEmbedderFiles()
                if (files == null) {
                    disposeLocked()
                    return null
                }
                val corrente = cache
                if (corrente != null && corrente.id == files.embedderId) return corrente
                disposeLocked()
                GemmaEmbedder(
                    id = files.embedderId,
                    modelPath = files.modelFile.absolutePath,
                    tokenizerPath = files.tokenizerFile.absolutePath,
                ).also { cache = it }
            }

        private fun disposeLocked() {
            cache?.let { runCatching { it.close() } }
            cache = null
        }
    }
