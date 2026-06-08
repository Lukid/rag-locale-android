package it.netseven.raglocale.retrieval

import kotlin.math.sqrt

/** Risultato del ranking: indice del vettore candidato e score di similarità coseno. */
data class RisultatoRanking(
    val indice: Int,
    val score: Double,
)

/**
 * Similarità coseno e top-K in Kotlin puro (design M2, D3): a scala demo lo scan
 * completo è corretto, semplice e didattico. Componente puro, unit-testato in JVM.
 */
object RankingCosine {
    fun similarita(
        a: FloatArray,
        b: FloatArray,
    ): Double {
        require(a.size == b.size) { "Dimensioni diverse: ${a.size} vs ${b.size}" }
        var prodotto = 0.0
        var normaA = 0.0
        var normaB = 0.0
        for (i in a.indices) {
            prodotto += a[i].toDouble() * b[i].toDouble()
            normaA += a[i].toDouble() * a[i].toDouble()
            normaB += b[i].toDouble() * b[i].toDouble()
        }
        if (normaA == 0.0 || normaB == 0.0) return 0.0
        return prodotto / (sqrt(normaA) * sqrt(normaB))
    }

    fun topK(
        query: FloatArray,
        vettori: List<FloatArray>,
        k: Int,
    ): List<RisultatoRanking> =
        vettori
            .mapIndexed { indice, vettore -> RisultatoRanking(indice, similarita(query, vettore)) }
            .sortedByDescending { it.score }
            .take(k)
}
