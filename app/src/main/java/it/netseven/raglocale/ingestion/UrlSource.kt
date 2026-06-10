package it.netseven.raglocale.ingestion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Recupero dell'HTML di una pagina (unica operazione di rete della pipeline). Astratto
 * dietro un'interfaccia così la mappatura degli errori in [UrlSource] è unit-testabile.
 * `scarica` è bloccante: [UrlSource] la invoca su [Dispatchers.IO]. Lancia [IOException] su
 * fallimento di rete o stato HTTP non riuscito; [IllegalArgumentException] su URL malformato.
 */
fun interface FetcherHttp {
    fun scarica(url: String): String
}

/**
 * Fetcher basato su OkHttp (già dipendenza del progetto). Tratta gli stati HTTP non 2xx come
 * errore esplicito, così la pagina non raggiungibile non lascia stato parziale nell'indice.
 */
class OkHttpFetcher(
    private val client: OkHttpClient = OkHttpClient(),
) : FetcherHttp {
    override fun scarica(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("risposta senza corpo")
        }
    }

    companion object {
        private const val USER_AGENT = "RagLocale/0.1 (ingestion documento on-device)"
    }
}

/**
 * Sorgente di ingestion da URL (task 4.4, design M2 D9): scarica la pagina e ne estrae il
 * contenuto principale via [EstrazioneHtml] (Readability4J con fallback al testo grezzo).
 * Un URL offline/timeout/4xx/5xx o malformato diventa [ErroreIngestion.ReteNonRaggiungibile]
 * senza che nulla venga scritto nell'indice — l'estrazione e l'indicizzazione partono solo
 * dopo un fetch riuscito.
 */
class UrlSource(
    private val estrazione: EstrazioneHtml = EstrazioneHtml(),
    private val fetcher: FetcherHttp = OkHttpFetcher(),
) {
    suspend fun estrai(url: String): EsitoEstrazione =
        withContext(Dispatchers.IO) {
            val html =
                try {
                    fetcher.scarica(url)
                } catch (e: IOException) {
                    return@withContext EsitoEstrazione.Errore(
                        ErroreIngestion.ReteNonRaggiungibile(e.message ?: "rete non disponibile"),
                    )
                } catch (e: IllegalArgumentException) {
                    return@withContext EsitoEstrazione.Errore(
                        ErroreIngestion.ReteNonRaggiungibile(e.message ?: "URL non valido"),
                    )
                }
            estrazione.estrai(html, url)
        }
}
