# RAG semantica on-device su Android — Design

- **Data:** 2026-05-29
- **Stato:** approvato in brainstorming, pronto per il piano di implementazione
- **Tipo:** demo didattica (prototipo, non prodotto)

## Obiettivo

Mostrare, dal vivo e in modo comprensibile, che una **pipeline RAG semantica classica gira interamente sul telefono**: nessuna API cloud per l'inferenza, dato privato che non lascia il device. È un artefatto didattico: il valore è far *capire* il RAG, non costruire un prodotto.

Il messaggio in una frase: *"il RAG non è magia cloud — ecco ogni pezzo che gira offline nel tuo telefono"*.

## Scope

### Dentro
- RAG **dense-only** ("old classic"): chunking → embedding → cosine top-K → risposta grounded.
- Tutto **locale**: embedder, vector store e LLM sul device.
- Ingestion da **tre sorgenti**: file testo/markdown, PDF, URL.
- **Selettore/download modello in-app** (default Gemma 4 E2B), stile AI Edge Gallery.
- UI didattica: per ogni domanda mostra i chunk recuperati con i loro **score di similarità** e quali hanno alimentato la risposta.
- Risposte con **citazione** dei chunk usati.

### Fuori (YAGNI — esplicitamente non ora)
- Hybrid retrieval (BM25) e re-ranking → possibile *fase 2*, non in questa demo.
- OCR per PDF scansionati senza layer testo.
- Corpus multi-documento / knowledge base persistente che cresce nel tempo.
- iOS / cross-platform (target solo Android).

## Vincoli e device target

- **Pavimento hardware:** Xiaomi Poco X6 Pro 5G (Dimensity 8300-Ultra 4nm, 8GB RAM reali, UFS 4.0). Mid-range 2024, non flagship.
- Gli "8GB + 4GB" sono 8GB reali + 4GB virtuali (swap): per l'LLM contano **solo gli 8GB reali**. Tolto il SO, budget app ~4-5GB.
- Google dichiara l'AI Edge RAG SDK *"optimized for Pixel 8/9, S23/S24"*: siamo sotto il target ufficiale → modelli quantizzati obbligatori e latenza non da flagship, accettabile per una demo.

## Modelli (tutti locali)

> Aggiornato a maggio 2026 dopo ricerca. Il sample dell'SDK usa ancora Gemma-3 1B + Gecko: scelte datate. Target attuale → Gemma 4 E2B + EmbeddingGemma.

| Ruolo | Modello | Note |
|---|---|---|
| LLM | **Gemma 4 E2B** (variante "edge"/phone), 4-bit (2-bit opzionale), via MediaPipe LLM Inference su **LiteRT-LM** | ~1.3GB su disco, **2-3GB RAM a runtime** (<1.5GB col 2-bit su alcuni device). Context **128K**, 140+ lingue. Multimodale, ma usiamo **solo testo**. |
| Embedder | **EmbeddingGemma** (308M, on-device, basato su Gemma 3) — raccomandato per RAG mobile. **Fallback: Gecko** quantizzato (default dell'SDK, 768 dim) | EmbeddingGemma è l'embedder moderno; Gecko resta la rete di sicurezza out-of-box |

- **Selettore modello in-app** (come la AI Edge Gallery): l'utente scarica/sceglie il modello dall'app. Default **E2B** (sta negli 8GB del Poco); **E4B** (~4-5GB RAM) opzionale solo per device più capienti.
- **Fondazione:** AI Edge RAG SDK (`com.google.ai.edge.localagents:localagents-rag`) per embedder, vector store e generazione. Per il dense-only usiamo l'SDK *out-of-the-box* (`RetrievalAndInferenceChain` o composizione diretta dei componenti) — scenario nativo dell'SDK, minor rischio.
- ⚠️ **Supporto Gemma 4 nell'SDK da verificare in build**: l'LLM Inference API carica LiteRT-LM (quindi atteso ok), ma le docs RAG citano per nome solo Gemma 3/3n. **Fallback sicuro: Gemma 3n**, esplicitamente supportato.

## Architettura

Confine chiave: **ogni sorgente produce testo normalizzato**; da lì in poi la pipeline non sa più da dove venisse il documento.

```
DocumentSource.extract() : NormalizedText
  ├─ TextFileSource (.txt/.md)        → diretto
  ├─ PdfSource (.pdf)                 → PdfBox-Android (estrazione testo)
  └─ UrlSource (link)                 → Jsoup + Readability4J (richiede rete 1 volta)

NormalizedText → Chunker(size, overlap) → List<Chunk>
Chunk → EmbeddingGemmaEmbedder → Vector(dim modello)
(Chunk.text + Vector) → SqliteVectorStore

# A ogni domanda:
Query → EmbeddingGemmaEmbedder → QueryVector
QueryVector → SqliteVectorStore.search(topK, cosine) → List<RetrievedChunk{text, score}>
RetrievedChunk[] → PromptBuilder → Gemma → Answer{text, citations}
```

### Componenti (unità a responsabilità singola)
1. **Ingestion** — `DocumentSource` con 3 implementazioni → `NormalizedText`. La sorgente URL è l'unica che tocca la rete.
2. **Chunker** — spezza il testo (size/overlap configurabili); deve tenere ragionevole il budget `topK × chunkSize` per RAM, latenza e batteria, anche se Gemma 4 E2B ha context ampia.
3. **Embedder** — wrapper su EmbeddingGemma; Gecko resta fallback SDK se EmbeddingGemma non e' disponibile.
4. **Vector store** — `SqliteVectorStore` (persistente); ricerca cosine top-K.
5. **Generator** — `PromptBuilder` (chunk + domanda → prompt grounded) + chiamata Gemma; estrae le citazioni.
6. **UI didattica** — schermata di ingestion (scegli file/PDF/incolla link) + chat; pannello che espone chunk recuperati, score e chunk citati.
7. **Model manager** — download e selezione del modello in-app (E2B default, E4B opzionale); check spazio storage e stato del modello.

## Il perno didattico: il "test della parafrasi"

Fai una domanda con parole **che non compaiono** nel documento, e la ricerca semantica trova lo stesso il chunk giusto → dimostrazione viscerale di cosa comprano gli embedding rispetto al match per parola chiave. La UI mostra gli score di similarità a supporto. (Questo motiva da sé perché un giorno aggiungeresti BM25/hybrid — quando le parole esatte contano — senza costruirlo ora.)

## Gestione errori

- **Download modelli:** progress, ripresa, check spazio storage.
- **GPU delegate (Mali):** se l'init GPU fallisce → fallback automatico a CPU + avviso ("più lento"). *Vedi rischio #1.*
- **OOM in inferenza:** ridurre topK / budget chunk.
- **URL:** offline/timeout/4xx/paywall/pagina JS-heavy → se Readability fallisce, fallback a testo grezzo o errore chiaro.
- **PDF scansionato (no layer testo):** rilevare e informare l'utente (OCR fuori scope).
- **Documento vuoto / troppo grande:** limite numero chunk con messaggio.

## Rischi

1. **[ALTO — spike per primo] Accelerazione su GPU Mali/MediaTek.** L'LLM Inference API auto-seleziona GPU/NPU/CPU, ma è collaudata bene su Adreno/Tensor e incerta su Mali. *Primo task in assoluto: far partire Gemma 4 E2B via LiteRT sul Poco e capire se gira accelerata o cade in CPU* — determina la latenza reale.
2. **Memoria, non context window.** Gemma 4 E2B ha 128K di context (ampio): il vincolo non è più la finestra del prompt ma la **RAM** (~2-3GB a runtime sugli 8GB del Poco). Tenere comunque `topK` ragionevole per latenza/batteria.
3. **Qualità risposte di un modello edge piccolo** → gestire le aspettative; grounding + citazioni mitigano le allucinazioni.
4. **Variabilità estrazione testo da URL** (boilerplate, pagine dinamiche).
5. **Supporto ufficiale Gemma 4 nell'AI Edge RAG SDK non confermato** (docs citano Gemma 3/3n). Mitigazione: fallback a **Gemma 3n**, supportato esplicitamente.

## Testing

- **Unit:** Chunker (dimensioni/overlap/casi limite), ranking cosine, estrazione `NormalizedText` per ciascuna sorgente (PDF e HTML di esempio), formato `PromptBuilder`.
- **Manuale su device (il Poco):** download modelli, GPU vs CPU, latenza, memoria, e il test della parafrasi end-to-end.

## Decisioni prese in brainstorming

- Dense-only invece di hybrid+rerank → più robusto in demo, più leggero sul Poco, lezione più pulita; mappa 1:1 sull'SDK.
- LLM-as-reranker abbandonato insieme al rerank (non serve nel dense-only).
- Documento singolo ma **lungo** (abbastanza chunk perché il retrieval conti); 3 sorgenti di ingestion (testo, PDF, URL).
- Android nativo Kotlin + AI Edge RAG SDK.
- **Modelli aggiornati (ricerca maggio 2026): Gemma 4 E2B (LLM) + EmbeddingGemma (embedder)**, al posto del datato Gemma-3 1B + Gecko del sample. Fallback: Gemma 3n / Gecko.
- **Selettore modello in-app** (stile AI Edge Gallery), default E2B.
