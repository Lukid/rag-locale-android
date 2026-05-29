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

| Ruolo | Modello | Note |
|---|---|---|
| Embedder | **Gecko** quantizzato, variante 256 token | 768 dim |
| LLM | **Gemma-3 1B**, int4, via LLM Inference API | unico modello generativo |

Fondazione: **AI Edge RAG SDK** (`com.google.ai.edge.localagents:localagents-rag`) per embedder, vector store e LLM. Per il dense-only usiamo l'SDK *out-of-the-box* (`RetrievalAndInferenceChain` o composizione diretta dei componenti) — è lo scenario nativo dell'SDK, percorso a minor rischio.

## Architettura

Confine chiave: **ogni sorgente produce testo normalizzato**; da lì in poi la pipeline non sa più da dove venisse il documento.

```
DocumentSource.extract() : NormalizedText
  ├─ TextFileSource (.txt/.md)        → diretto
  ├─ PdfSource (.pdf)                 → PdfBox-Android (estrazione testo)
  └─ UrlSource (link)                 → Jsoup + Readability4J (richiede rete 1 volta)

NormalizedText → Chunker(size, overlap) → List<Chunk>
Chunk → GeckoEmbedder → Vector(768)
(Chunk.text + Vector) → SqliteVectorStore

# A ogni domanda:
Query → GeckoEmbedder → QueryVector
QueryVector → SqliteVectorStore.search(topK, cosine) → List<RetrievedChunk{text, score}>
RetrievedChunk[] → PromptBuilder → Gemma → Answer{text, citations}
```

### Componenti (unità a responsabilità singola)
1. **Ingestion** — `DocumentSource` con 3 implementazioni → `NormalizedText`. La sorgente URL è l'unica che tocca la rete.
2. **Chunker** — spezza il testo (size/overlap configurabili); deve tenere il prompt finale entro la context window di Gemma-3 1B (`topK × chunkSize` budget-aware).
3. **Embedder** — wrapper su Gecko; stessa funzione per chunk e query.
4. **Vector store** — `SqliteVectorStore` (persistente); ricerca cosine top-K.
5. **Generator** — `PromptBuilder` (chunk + domanda → prompt grounded) + chiamata Gemma; estrae le citazioni.
6. **UI didattica** — schermata di ingestion (scegli file/PDF/incolla link) + chat; pannello che espone chunk recuperati, score e chunk citati.

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

1. **[ALTO — spike per primo] GPU delegate di MediaPipe su GPU Mali/MediaTek.** Collaudato bene su Adreno/Tensor, incerto su Mali. *Primo task in assoluto: far partire Gemma-3 1B via LiteRT sul Poco e capire se gira su GPU o cade in CPU* — determina la latenza attesa.
2. **Context window di Gemma-3 1B piccola** → il budget `topK × chunkSize` va calibrato perché il prompt aumentato ci stia.
3. **Qualità risposte di un modello 1B** → gestire le aspettative; grounding + citazioni mitigano le allucinazioni.
4. **Variabilità estrazione testo da URL** (boilerplate, pagine dinamiche).

## Testing

- **Unit:** Chunker (dimensioni/overlap/casi limite), ranking cosine, estrazione `NormalizedText` per ciascuna sorgente (PDF e HTML di esempio), formato `PromptBuilder`.
- **Manuale su device (il Poco):** download modelli, GPU vs CPU, latenza, memoria, e il test della parafrasi end-to-end.

## Decisioni prese in brainstorming

- Dense-only invece di hybrid+rerank → più robusto in demo, più leggero sul Poco, lezione più pulita; mappa 1:1 sull'SDK.
- LLM-as-reranker abbandonato insieme al rerank (non serve nel dense-only).
- Documento singolo ma **lungo** (abbastanza chunk perché il retrieval conti); 3 sorgenti di ingestion (testo, PDF, URL).
- Android nativo Kotlin + AI Edge RAG SDK.
