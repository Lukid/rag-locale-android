package it.netseven.raglocale.store

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Serializzazione di un embedding ([FloatArray]) verso/da un BLOB SQLite ([ByteArray]).
 * Ordine dei byte fissato esplicitamente (little-endian) perché l'indice è persistente:
 * cambiare l'endianness invaliderebbe in silenzio gli indici già scritti.
 *
 * Componente puro (nessuna dipendenza Android), unit-testato in JVM.
 */
object EmbeddingBlob {
    private const val BYTE_PER_FLOAT = Float.SIZE_BYTES // 4

    fun aBytes(vettore: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vettore.size * BYTE_PER_FLOAT).order(ByteOrder.LITTLE_ENDIAN)
        for (valore in vettore) buffer.putFloat(valore)
        return buffer.array()
    }

    fun daBytes(bytes: ByteArray): FloatArray {
        require(bytes.size % BYTE_PER_FLOAT == 0) {
            "BLOB embedding corrotto: ${bytes.size} byte non è multiplo di $BYTE_PER_FLOAT"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / BYTE_PER_FLOAT) { buffer.getFloat() }
    }
}
