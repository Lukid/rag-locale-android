package it.netseven.raglocale.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.getStringOrNull
import it.netseven.raglocale.retrieval.ChunkRecuperato
import it.netseven.raglocale.retrieval.RankingCosine

/**
 * Vector store persistente su SQLite (design M2 D3). I chunk e i loro embedding (BLOB)
 * sono persistiti; la ricerca carica tutto in memoria e applica il cosine top-K di
 * [RankingCosine]. A scala demo (un documento lungo → centinaia di chunk, pochi MB) lo
 * scan completo è corretto, semplice e didattico.
 *
 * L'embedder usato per costruire l'indice è registrato come metadato: serve al controllo
 * di coerenza indice/query ([CoerenzaEmbedder]) per non restituire risultati incoerenti
 * quando l'embedder attivo cambia.
 */
class SqliteVectorStore(
    context: Context,
) : SQLiteOpenHelper(context, NOME_DB, null, VERSIONE_DB), VectorStore {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABELLA_CHUNK (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                documento TEXT NOT NULL,
                indice_chunk INTEGER NOT NULL,
                testo TEXT NOT NULL,
                embedding BLOB NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE TABLE $TABELLA_META (chiave TEXT PRIMARY KEY, valore TEXT NOT NULL)",
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        // Indice ricostruibile dai documenti d'origine: a fronte di un cambio di schema
        // si ricrea da zero invece di migrare (l'utente ri-indicizza).
        db.execSQL("DROP TABLE IF EXISTS $TABELLA_CHUNK")
        db.execSQL("DROP TABLE IF EXISTS $TABELLA_META")
        onCreate(db)
    }

    override fun indicizza(
        documento: String,
        chunks: List<ChunkDaIndicizzare>,
        embedderId: String,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (chunk in chunks) {
                val valori =
                    ContentValues().apply {
                        put("documento", documento)
                        put("indice_chunk", chunk.indiceChunk)
                        put("testo", chunk.testo)
                        put("embedding", EmbeddingBlob.aBytes(chunk.embedding))
                    }
                db.insert(TABELLA_CHUNK, null, valori)
            }
            val meta =
                ContentValues().apply {
                    put("chiave", CHIAVE_EMBEDDER)
                    put("valore", embedderId)
                }
            db.insertWithOnConflict(TABELLA_META, null, meta, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun cerca(
        embeddingQuery: FloatArray,
        topK: Int,
    ): List<ChunkRecuperato> {
        val righe = mutableListOf<RigaChunk>()
        val embedding = mutableListOf<FloatArray>()
        readableDatabase
            .query(TABELLA_CHUNK, arrayOf("documento", "indice_chunk", "testo", "embedding"), null, null, null, null, null)
            .use { cursore ->
                val colDocumento = cursore.getColumnIndexOrThrow("documento")
                val colIndice = cursore.getColumnIndexOrThrow("indice_chunk")
                val colTesto = cursore.getColumnIndexOrThrow("testo")
                val colEmbedding = cursore.getColumnIndexOrThrow("embedding")
                while (cursore.moveToNext()) {
                    righe += RigaChunk(cursore.getString(colDocumento), cursore.getInt(colIndice), cursore.getString(colTesto))
                    embedding += EmbeddingBlob.daBytes(cursore.getBlob(colEmbedding))
                }
            }

        return RankingCosine.topK(embeddingQuery, embedding, topK).map { risultato ->
            val riga = righe[risultato.indice]
            ChunkRecuperato(
                testo = riga.testo,
                score = risultato.score,
                documento = riga.documento,
                indiceChunk = riga.indiceChunk,
            )
        }
    }

    override fun embedderIndice(): String? =
        readableDatabase
            .query(TABELLA_META, arrayOf("valore"), "chiave = ?", arrayOf(CHIAVE_EMBEDDER), null, null, null)
            .use { cursore ->
                if (cursore.moveToFirst()) cursore.getStringOrNull(0) else null
            }

    override fun svuota() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABELLA_CHUNK, null, null)
            db.delete(TABELLA_META, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private data class RigaChunk(
        val documento: String,
        val indiceChunk: Int,
        val testo: String,
    )

    companion object {
        private const val NOME_DB = "rag_vector_store.db"
        private const val VERSIONE_DB = 1
        private const val TABELLA_CHUNK = "chunks"
        private const val TABELLA_META = "indice_meta"
        private const val CHIAVE_EMBEDDER = "embedder_id"
    }
}
