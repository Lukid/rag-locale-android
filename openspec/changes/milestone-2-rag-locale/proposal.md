# Proposal: milestone-2-rag-locale

## Why

Il bivio del Milestone 2 è stato sciolto il 2026-06-06: l'anima del progetto resta **A — "RAG tutto nel telefono"** (il design originale del 2026-05-29), con l'anima B (host MCP) parcheggiata come possibile M3. Il Milestone 1 ha consegnato il terreno comune (chat on-device su LiteRT-LM, GPU validata, model manager): ora va costruita la parte che dà senso didattico al progetto — la pipeline RAG semantica che gira interamente sul device, culminante nel "test della parafrasi".

## What Changes

- **Pipeline RAG dense-only fatta in casa** sul nostro `InferenceEngine` LiteRT-LM esistente: ingestion → chunking → embedding → cosine top-K → risposta grounded con citazioni. Scostamento dal design originale: NON si adotta l'AI Edge RAG SDK out-of-the-box (`RetrievalAndInferenceChain`), che porterebbe un secondo stack di inferenza (MediaPipe `tasks-genai`); i componenti sono fatti in casa e visibili — più coerente con la lezione.
- **Ingestion da tre sorgenti** (file testo/markdown, PDF, URL), ognuna normalizzata in `NormalizedText`: da lì la pipeline è indipendente dall'origine.
- **Embedder on-device (EmbeddingGemma, fallback Gecko)**: il runtime con cui eseguirlo nel nostro stack è il rischio #1 del milestone → **spike dedicato come primo task** (stesso pattern dello spike GPU che ha de-riskato M1).
- **Retrieval dietro interfaccia "tool-shaped"** (`searchDocuments(query) → chunks+scores`): vincolo architetturale ereditato dal bivio, così l'eventuale M3 (host MCP) promuove la retrieval a tool locale senza riscritture.
- **UI didattica**: pannello che mostra i chunk recuperati con score di similarità e quali hanno alimentato la risposta; risposte con citazioni.
- **Model manager esteso agli embedder**: la spec M1 limitava esplicitamente il catalogo ai soli LLM; ora serve un secondo tipo di modello, con budget RAM da gestire (LLM ~3GB + embedder ~308M negli 8GB del Poco).
- **Criterio di accettazione end-to-end: il "test della parafrasi"** — domanda con parole che non compaiono nel documento, il retrieval semantico trova comunque il chunk giusto.

## Capabilities

### New Capabilities

- `document-ingestion`: acquisizione di un documento da tre sorgenti (testo/markdown, PDF, URL) → `NormalizedText` → chunking (size/overlap configurabili) → embedding → indicizzazione persistente nel vector store; gestione errori di sorgente (PDF senza layer testo, URL irraggiungibile/paywall, documento vuoto o troppo grande).
- `semantic-retrieval`: data una query, embedding della query e ricerca cosine top-K sul vector store SQLite; restituisce chunk con score di similarità; esposta dietro interfaccia tool-shaped (`searchDocuments(query) → chunks+scores`) indipendente dalla UI.
- `grounded-answers`: costruzione del prompt grounded sui chunk recuperati, generazione con il modello residente, risposta con citazioni dei chunk usati; trasparenza didattica (chunk, score e citazioni visibili per ogni domanda).

### Modified Capabilities

- `model-manager`: il catalogo si estende ai modelli **embedder** (cade il vincolo M1 "solo LLM, nessun embedder"); selezione/stato/rimozione valgono anche per l'embedder; il check storage e il ciclo di vita devono considerare due modelli residenti (LLM + embedder).

## Impact

- **Codice nuovo**: package `ingestion` (sorgenti + `NormalizedText` + `Chunker`), `retrieval` (embedder, `SqliteVectorStore`, ranking cosine), estensione di `chat` (PromptBuilder grounded + citazioni) e `ui` (schermata ingestion, pannello didattico).
- **Codice esistente toccato**: `modelmanager` (catalogo/stato per embedder), `InferenceEngine` riusato senza modifiche sostanziali (resta l'unico motore LLM), schermata chat (pannello retrieval).
- **Dipendenze nuove**: PdfBox-Android (estrazione testo PDF), Jsoup + Readability4J (estrazione da URL), runtime embedder (da determinare con lo spike: LiteRT puro, modulo embedder di `localagents-rag`, o altro).
- **Rete**: invariata rispetto al design — l'unica rete è `UrlSource` (fetch su richiesta) e il download modelli; l'inferenza resta 100% on-device.
- **RAM**: budget da validare sul Poco con LLM + embedder residenti insieme.
- **Test**: unit JVM per Chunker, ranking cosine, estrazione `NormalizedText`, PromptBuilder; test manuale end-to-end della parafrasi sul Poco.
