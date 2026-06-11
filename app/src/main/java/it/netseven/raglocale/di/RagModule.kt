package it.netseven.raglocale.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.netseven.raglocale.retrieval.EmbedderProvider
import it.netseven.raglocale.retrieval.FornitoreEmbedder
import it.netseven.raglocale.retrieval.RicercaDocumenti
import it.netseven.raglocale.retrieval.RicercaSemantica
import it.netseven.raglocale.store.SqliteVectorStore
import it.netseven.raglocale.store.VectorStore
import javax.inject.Singleton

/**
 * Lega le interfacce del grafo RAG alle loro implementazioni (gruppo 6). Lo store SQLite, il
 * fornitore dell'embedder e la ricerca semantica sono singleton: l'indice e il runtime nativo
 * dell'embedder vanno condivisi tra ingestion e query.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RagModule {
    @Binds
    @Singleton
    abstract fun bindVectorStore(impl: SqliteVectorStore): VectorStore

    @Binds
    @Singleton
    abstract fun bindFornitoreEmbedder(impl: EmbedderProvider): FornitoreEmbedder

    @Binds
    @Singleton
    abstract fun bindRicercaDocumenti(impl: RicercaSemantica): RicercaDocumenti
}
