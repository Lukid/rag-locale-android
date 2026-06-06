# Design: milestone-2-rag-locale

## Context

Il Milestone 1 ha consegnato chat on-device funzionante su **LiteRT-LM 0.12.0 diretto** (non sull'AI Edge RAG SDK assunto dal design originale 2026-05-29): `InferenceEngine` con backend GPU/CPU e fallback, modello residente con keep-alive, model manager con import da file e validazione d'integrità (md5 — lezione del file corrotto), streaming verificato sul Poco X6 Pro.

Lo spike GPU (memoria `spike-gemma4-e2b-poco`) dà i numeri che governano questo design: prefill GPU 559 tok/s (quasi gratis), decode ~10 tok/s GPU / ~13,5 CPU, TTFT 1,9 s GPU vs 8 s CPU a 1024 di contesto. Il workload RAG (contesto lungo, risposta breve) è quello in cui la GPU vince.

Il bivio M2 è sciolto (2026-06-06): anima A (RAG locale), anima B (host MCP) parcheggiata come possibile M3 — da cui il vincolo dell'interfaccia tool-shaped sulla retrieval.

Vincoli ereditati: pavimento hardware Poco X6 Pro (8 GB RAM reali, budget app ~4-5 GB), inferenza 100% on-device, nomi e commenti in italiano, componenti puri testabili senza Android.

## Goals / Non-Goals

**Goals:**
- Pipeline RAG dense-only end-to-end on-device: ingestion (3 sorgenti) → chunking → embedding → cosine top-K → risposta grounded con citazioni.
- Ogni componente della pipeline visibile e fatto in casa (valore didattico), unit-testabile in JVM dove puro.
- Retrieval dietro interfaccia tool-shaped riusabile (`searchDocuments(query) → chunks+scores`).
- UI didattica: chunk recuperati, score di similarità, citazioni evidenziate.
- Superare il "test della parafrasi" sul Poco (criterio di accettazione end-to-end).
- De-risk dell'embedder con uno spike dedicato prima del resto (stesso pattern M1).

**Non-Goals:**
- Hybrid retrieval (BM25), re-ranking, OCR per PDF scansionati (YAGNI del design originale).
- Knowledge base multi-documento persistente che cresce nel tempo (documento singolo ma lungo).
- Host MCP / tool-loop (eventuale M3).
- Download in-app dei modelli con ripresa e flusso token HF (resta rinviato come da M1: acquisizione via import da file).
- iOS / cross-platform.

## Decisions

### D1 — Pipeline fatta in casa, non AI Edge RAG SDK
Il design originale assumeva `localagents-rag` (`RetrievalAndInferenceChain`) come fondazione, ma quella chain porta il **suo** motore LLM (MediaPipe `tasks-genai`): adottarla significherebbe due stack di inferenza o buttare l'`InferenceEngine` appena validato. Si costruiscono in casa Chunker, vector store, ranking e PromptBuilder sul motore esistente. Alternativa considerata e scartata: adozione integrale dell'SDK (perde il lavoro M1 e nasconde i pezzi della lezione). Resta aperta (D2) la possibilità di usare il **solo modulo embedder** dell'SDK.

### D2 — Runtime dell'embedder: spike prima di tutto (rischio #1)
EmbeddingGemma on-device nel nostro stack è l'ignoto principale: formato dell'artefatto (LiteRT `.tflite`? `.litertlm`?), tokenizzazione (sentencepiece), API di esecuzione. Candidati in ordine di preferenza:
1. **LiteRT puro** (interprete `.tflite` + tokenizer): nessuna dipendenza nuova pesante, coerente con lo stack;
2. **modulo embedder di `localagents-rag`** (`GemmaEmbeddingModel`/`GeckoEmbeddingModel`) usato standalone, senza la chain: meno codice nostro, da verificare la convivenza con LiteRT-LM;
3. **fallback Gecko** (768 dim, default storico dell'SDK) se EmbeddingGemma non è praticabile.
Lo spike (primo task del milestone) decide tra i tre con criteri espliciti: embedding calcolati correttamente (similarità sensata su frasi di prova in italiano), latenza per chunk, RAM, attrito di integrazione. L'esito va annotato qui.

### D3 — Vector store: SQLite per persistenza, cosine brute-force in RAM
Scala demo: un documento lungo → centinaia di chunk; a 768 dim float sono pochi MB. Quindi: persistenza su **SQLite** (tabella `chunks`: id, documento, testo, embedding BLOB, metadati), ricerca con **scan completo in memoria + cosine in Kotlin puro** (top-K con heap). Niente estensioni vettoriali (sqlite-vec) né ANN: complessità non giustificata a questa scala e il ranking fatto a mano È la lezione. Se gli embedding sono normalizzati, cosine = dot product (da verificare nello spike e annotare).

### D4 — Interfaccia tool-shaped per la retrieval
Un'interfaccia Kotlin pura, senza dipendenze Android né UI:
```kotlin
interface RicercaDocumenti {
    suspend fun cerca(query: String, topK: Int): List<ChunkRecuperato>
    // ChunkRecuperato { testo, score, riferimentoDocumento, indiceChunk }
}
```
UI didattica e PromptBuilder consumano **solo** questa interfaccia. In un eventuale M3 diventa un tool MCP locale senza riscritture. (Nomi indicativi: definitivi in implementazione, in italiano.)

### D5 — Chunker: caratteri con rispetto dei confini di frase
Chunking per caratteri con size/overlap configurabili (default iniziali: ~1000 caratteri, overlap ~15%) e taglio preferenziale ai confini di frase/paragrafo. Kotlin puro, zero dipendenze, unit test sui casi limite (testo corto, overlap ≥ size, frasi lunghissime). I default si tarano sul Poco durante la validazione; lo spike GPU dice che il prefill è quasi gratis, quindi `topK` può essere generoso — il vincolo è RAM e lunghezza risposta, non TTFT.

### D6 — PromptBuilder e citazioni: chunk numerati, parsing dei marcatori
I chunk recuperati entrano nel prompt numerati (`[1]`, `[2]`, …) con istruzione esplicita di rispondere solo dal contesto citando i numeri. Le citazioni si estraggono dal testo generato col parsing dei marcatori `[n]`. Rischio noto: la compliance di un modello edge piccolo nel citare; mitigazione in D-Risks. Fallback di visualizzazione: se nessun marcatore valido, la UI mostra comunque i chunk recuperati con score (la trasparenza didattica non dipende dalla compliance del modello).

### D7 — Due modelli residenti: embedder leggero sempre, LLM come oggi
L'embedder (~308M parametri) si carica on-demand al primo uso (ingestion o query) e resta residente: costo RAM contenuto rispetto al LLM (~3 GB). Il ciclo di vita del LLM non cambia (keep-alive M1). Ordine in una query RAG: embed della query (embedder) → retrieval → generazione (LLM). In caso di pressione di memoria: prima ridurre `topK`/budget chunk, poi valutare unload dell'embedder dopo l'ingestion. Budget RAM da misurare sul Poco con entrambi residenti (task di validazione).

### D8 — Distribuzione del modello embedder: stesso pattern import M1
L'embedder entra nel catalogo del model manager come secondo **tipo** di modello (LLM | EMBEDDER), con stato/selezione/rimozione analoghi e acquisizione via import da file (`adb push` + import, o SAF) con **verifica d'integrità** (md5/SHA — lezione M1: la sola dimensione non basta). Il download in-app resta fuori scope come per il LLM.

### D9 — Ingestion: staging interno per sorgente
Ordine di implementazione: prima `.txt/.md` (pipeline end-to-end subito esercitabile), poi PDF (PdfBox-Android), poi URL (Jsoup + Readability4J, fallback a testo grezzo se Readability fallisce). Ogni sorgente produce `NormalizedText`; da lì in poi la pipeline è identica. Errori per sorgente gestiti come da design originale (PDF senza layer testo rilevato e comunicato, URL offline/4xx/paywall con errore chiaro, documento vuoto/troppo grande con limite chunk e messaggio).

### D10 — Backend: GPU default per il LLM, CPU per l'embedder salvo spike contrario
Il workload RAG (prefill lungo) è il caso in cui la GPU vince (TTFT 1,9 s vs 8 s). Default GPU per il LLM confermato, selezione manuale e fallback restano quelli di M1. Per l'embedder si parte da CPU (modello piccolo, latenza attesa trascurabile); lo spike può smentire. Il benchmark formale GPU vs CPU rinviato da M1 rientra nella validazione su device di questo milestone.

## Risks / Trade-offs

- **[Runtime EmbeddingGemma impraticabile nel nostro stack]** → spike come primo task; fallback a catena: modulo embedder SDK → Gecko. Se anche Gecko fallisse (improbabile, è il default storico), riconsiderare MediaPipe TextEmbedder prima di toccare l'architettura.
- **[RAM insufficiente con LLM + embedder residenti]** → misura esplicita sul Poco in validazione; mitigazioni in ordine: ridurre `topK`/budget chunk, unload embedder post-ingestion, ridurre cap token output.
- **[Citazioni inaffidabili dal modello edge]** → iterare il prompt (few-shot minimale); la UI non dipende dalla compliance: chunk e score sono mostrati comunque (D6). Il grounding resta dimostrabile anche con citazioni imperfette.
- **[Variabilità estrazione PDF/URL]** → staging D9: la pipeline si valida su testo puro prima di esporsi alla variabilità delle altre sorgenti; errori chiari per caso (no layer testo, paywall, JS-heavy).
- **[Trade-off: brute-force cosine non scala]** → accettato consapevolmente: a scala demo (centinaia di chunk) è corretto, semplice e didattico; il confine D4 permette di sostituire lo store senza toccare i consumatori.
- **[Trade-off: niente SDK = più codice nostro]** → accettato: è il punto della lezione; i componenti puri hanno unit test JVM a compensare.

## Open Questions

- **Formato e fonte dell'artefatto EmbeddingGemma** per on-device (HF `litert-community`? dimensione embedding effettiva? tokenizer incluso?) — lo risolve lo spike.
- **Convivenza `localagents-rag` (solo embedder) + LiteRT-LM** nello stesso processo, se si sceglie il candidato 2 — lo risolve lo spike.
- **Embedding normalizzati o no** (cosine vs dot product) — lo risolve lo spike.
- **Default chunk size/overlap/topK** per il Poco — si tarano in validazione col documento di prova del test della parafrasi.
- **Prompt template per EmbeddingGemma** (prefissi tipo `task: search result | query:` raccomandati per retrieval) — verificare nello spike se l'artefatto on-device li richiede.
