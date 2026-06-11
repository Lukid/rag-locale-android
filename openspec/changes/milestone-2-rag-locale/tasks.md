# Tasks: milestone-2-rag-locale

## 1. Spike embedder (de-risk del rischio #1 — primo task assoluto)

- [x] 1.1 Ricerca dell'artefatto EmbeddingGemma per on-device: fonte (HF `litert-community`?), formato (`.tflite`/`.litertlm`), dimensione embedding, tokenizer incluso, eventuali prefissi di task per il retrieval; annotare le coordinate — _✅ 2026-06-06: `litert-community/embeddinggemma-300m` (`.tflite` mixed-precision per seq length + `sentencepiece.model`); candidato promosso: modulo embedder `localagents-rag:0.3.0` (`GemmaEmbeddingModel`, prefissi gestiti dall'SDK, JNI self-contained); LiteRT puro retrocesso (sample solo C++, tokenizer JVM mancante); esiti completi in design.md D2._
- [x] 1.2 Far girare l'embedder sul Poco nel nostro stack provando i candidati in ordine (D2): LiteRT puro → modulo embedder di `localagents-rag` standalone → fallback Gecko; misurare latenza per chunk e RAM — _✅ 2026-06-06: `GemmaEmbeddingModel` (localagents-rag 0.3.0, candidato promosso dalla ricerca 1.1) su CPU, seq512: init 1,6 s, ~2,2 s/documento; convivenza in-process con LiteRT-LM dimostrata (`EmbedderSmokeTest`, OK 2 tests in 18,1 s)._
- [x] 1.3 Verificare la qualità degli embedding su frasi di prova in italiano (similarità sensata, inclusa una coppia di parafrasi) e se i vettori sono normalizzati (cosine vs dot product) — _✅ 2026-06-06: parafrasi separata nettamente (sim 0,70/0,61 vs 0,14 per l'estraneo); vettori normalizzati (norma 1,0) → cosine = dot product._
- [x] 1.4 Annotare l'esito dello spike in `design.md` (decisione D2 sciolta, Open Questions chiuse); se nessun candidato è praticabile, fermarsi e ridiscutere il design — _✅ D2 sciolta: GemmaEmbeddingModel su CPU seq512; Open Questions convivenza e normalizzazione chiuse in design.md._

## 2. Pipeline pura (Kotlin JVM, senza dipendenze Android)

- [x] 2.1 `Chunker`: size/overlap configurabili, tagli preferenziali ai confini di frase/paragrafo, casi limite (testo corto, overlap ≥ size) + unit test — _✅ TDD, 9 test (vuoto, testo corto, no overflow, copertura senza buchi, overlap, taglio a frase, offset reale, overlap ≥ dimensione)._
- [x] 2.2 Ranking cosine top-K in Kotlin puro + unit test con vettori deterministici — _✅ TDD, 9 test (identici/ortogonali/opposti, scala-invarianza, vettore nullo, ordinamento top-K, k</> candidati, lista vuota)._
- [x] 2.3 Interfaccia tool-shaped della retrieval (`cerca(query, topK) → chunk con testo/score/riferimento`) e tipi condivisi (`NormalizedText`, chunk) — contratto stabile per UI, PromptBuilder e futuro M3 — _✅ `RicercaDocumenti` + `ChunkRecuperato`, `NormalizedText` (pkg ingestion)._
- [x] 2.4 `PromptBuilder` grounded: chunk numerati + istruzione di rispondere solo dal contesto citando i numeri; parsing dei marcatori `[n]` dalla risposta + unit test (incluso il caso senza marcatori validi) — _✅ TDD, 7 test (numerazione/ordine/istruzioni, citazioni valide, fuori range scartate, nessun marcatore, duplicati, multi-cifra)._

## 3. Vector store e indicizzazione

- [x] 3.1 `SqliteVectorStore`: schema persistente (documento, chunk, embedding BLOB, metadati incluso l'embedder usato per l'indice) — _✅ `SqliteVectorStore` (SQLiteOpenHelper): tabella `chunks` (id, documento, indice_chunk, testo, embedding BLOB) + tabella `indice_meta` (embedder_id); serializzazione embedding ↔ BLOB isolata in `EmbeddingBlob` (little-endian fissato, round-trip puro)._
- [x] 3.2 Implementazione della ricerca: embedding della query → scan in memoria + cosine top-K (D3) — _✅ `cerca()` carica tutti i chunk in memoria e riusa `RankingCosine.topK`; risultati mappati a `ChunkRecuperato` (testo/score/documento/indiceChunk), ordinati per score decrescente._
- [x] 3.3 Coerenza embedder indice/query: rilevare il mismatch e proporre la re-indicizzazione invece di restituire risultati incoerenti — _✅ `CoerenzaEmbedder.verifica` (puro): `IndiceVuoto`/`Coerente`/`Incoerente`; lo store espone `embedderIndice()` come metadato. Il cablaggio nel flusso di query e la proposta in UI arrivano nel gruppo 6._
- [x] 3.4 Unit test dello store: round-trip di persistenza e ricerca con embedding finti deterministici — _✅ 14 test TDD: `SqliteVectorStoreTest` su SQLite reale via Robolectric (6: round-trip, ordine+topK, embedderIndice, svuota, indice vuoto, sopravvivenza alla riapertura del DB — no device, no wipe), `EmbeddingBlobTest` (5), `CoerenzaEmbedderTest` (3)._

## 4. Ingestion (staging per sorgente — D9)

- [x] 4.1 `TextFileSource` (.txt/.md via SAF) → `NormalizedText`; limiti documento (vuoto, troppo grande) con messaggi chiari — _✅ TDD. `TextFileSource` legge da `InputStream` (il cablaggio SAF/Uri vive nel ViewModel, gruppo 6) e passa per il `TextNormalizer` condiviso (fine riga, trattini morbidi/zero-width/BOM/NBSP, accenti preservati); tipi-risultato `EsitoEstrazione`/`ErroreIngestion` con messaggio pronto per la UI; documento vuoto → `DocumentoVuoto` (il "troppo grande" è controllato dalla pipeline, 4.2). 14 test (10 normalizer + 4 source)._
- [x] 4.2 Pipeline di ingestion end-to-end con progresso (chunk N/M) e indicizzazione persistente — esercitata sulla sola sorgente testo — _✅ `PipelineIngestion` (chunk → embed → indicizza) con callback di progresso (processati/totale) e limite max-chunk (`DocumentoTroppoGrande`). Introdotto il seam puro `Embedder` (id + embedDocumento/embedQuery) così l'orchestrazione è testabile in JVM con un embedder finto; niente stato parziale (tutti gli embedding prima della scrittura, `indicizza` transazionale). 4 test su `SqliteVectorStore` reale via Robolectric: persistenza+riapertura, progresso fino al totale, documento vuoto, oltre-limite senza scritture._
- [x] 4.3 `PdfSource` con PdfBox-Android; rilevamento del PDF senza layer testo (OCR fuori scope, messaggio chiaro) — _✅ `PdfSource` (PdfBox-Android `PDFTextStripper`); testo estratto vuoto = nessun layer testuale → `PdfSenzaTesto` (OCR fuori scope), input illeggibile → `LetturaFallita`. 3 test Robolectric con PDF generati in-test (testo, pagina vuota, input non-PDF), nessun binario nel repo._
- [x] 4.4 `UrlSource` con Jsoup + Readability4J; fallback al testo grezzo se Readability fallisce; errori rete chiari senza stato parziale nell'indice — _✅ `EstrazioneHtml` (pura): Readability4J isola l'articolo scartando il boilerplate, se il contenuto è troppo scarno ripiega sul testo grezzo Jsoup con avviso. `UrlSource` con `FetcherHttp` iniettabile (impl OkHttp): URL offline/timeout/HTTP non-2xx/malformato → `ReteNonRaggiungibile`, il fetch precede ogni scrittura (indice invariato). 6 test (3 estrazione HTML su fixture + 3 mapping errori di rete)._
- [x] 4.5 Unit test dell'estrazione `NormalizedText` per le tre sorgenti (fixture PDF e HTML di esempio) — _✅ coperto insieme a ciascuna sorgente: testo (`TextFileSourceTest`), PDF (`PdfSourceTest`, fixture generate con PdfBox), HTML/URL (`EstrazioneHtmlTest` + `UrlSourceTest`, fixture HTML). Gruppo 4: 27 test nuovi; suite 96/96 verde, `ktlintCheck` e `assembleDebug` ok. Dipendenze nuove: `pdfbox-android:2.0.27.0`, `jsoup:1.18.3`, `readability4j:1.0.8`._

## 5. Model manager esteso (embedder)

- [x] 5.1 Tipo di modello nel catalogo (LLM | embedder): EmbeddingGemma default embedder, Gecko come alternativa, metadati e stato per tipo — _✅ `ModelType {LLM, EMBEDDER}` su `ModelInfo`; `ModelCatalog.defaultFor(type)`/`forType(type)`. Default: Gemma 4 E2B-it (LLM), EmbeddingGemma 300M seq512 (embedder, con `expectedMd5` + `CompanionArtifact` tokenizer), Gecko come riserva non-default (coordinate non verificate). `ModelManagerScreen` raggruppa ed etichetta per tipo. 8 test `ModelCatalogTest`._
- [x] 5.2 Selezione dell'embedder attivo (persistita) e rimozione, in analogia col LLM attivo — _✅ `PreferencesRepository.activeEmbedderId` (chiave DataStore dedicata); `ModelRepository.setActive` instrada per tipo (LLM→activeModelId, embedder→activeEmbedderId), `activeModelFile()` resta type-safe sul LLM. `remove` cancella tutti i file del modello. UI: radiobutton attivo per tipo. Coperto da `ModelRepositoryTest` (Robolectric, selezione e rimozione)._
- [x] 5.3 Generalizzare l'import da file (`.part` + move atomico + checksum, scarto su mismatch) a entrambi i tipi di modello — _✅ `importFromUri(uri, ImportTarget)` per ogni file (principale o companion): staging `.part` → `ImportVerifier` (checksum md5 quando noto, altrimenti dimensione) → move atomico; scarto su mismatch cancella il `.part`. `FileChecksum.md5` in streaming. L'embedder importa due file (modello + tokenizer)._
- [x] 5.4 Unit test: macchina a stati per tipo, import con checksum non corrispondente — _✅ `ModelStatusResolver.resolveModel` (embedder pronto solo con modello+tokenizer entrambi plausibili) con 4 test; `ImportVerifier` puro con 7 test (incluso "stessa dimensione, md5 diverso → scarto", lezione M1); `ModelRepositoryTest` Robolectric (import ok a due file, checksum errato scartato senza lasciare il file, rimozione di entrambi). Suite 115/115 verde, `ktlintCheck` e `assembleDebug` ok._

## 6. Generazione grounded e UI didattica

- [ ] 6.1 Modalità RAG nella chat: domanda → retrieval (via interfaccia 2.3) → prompt grounded → generazione in streaming sul `InferenceEngine` esistente (stop e cap token invariati)
- [ ] 6.2 Pannello didattico: chunk recuperati con score per ogni domanda, chunk citati evidenziati, fallback senza marcatori (trasparenza indipendente dalla qualità della risposta)
- [ ] 6.3 Schermata di ingestion: scelta sorgente (file/PDF/URL), progresso, errori per caso
- [ ] 6.4 Grounding del "non lo so": iterare il prompt finché una domanda fuori contesto produce una dichiarazione di assenza invece di un'invenzione

## 7. Validazione su device (Poco X6 Pro)

- [ ] 7.1 Smoke test strumentato dell'embedder (caricamento + embedding di una frase, analogo a `InferenceSmokeTest`) — attenzione: niente `connectedAndroidTest` sul device di sviluppo (wipe dei dati!), usare `adb install -r -t` + `am instrument`
- [ ] 7.2 Misurare la RAM con LLM + embedder residenti insieme; tarare i default di `topK` e chunk size (D7)
- [ ] 7.3 **Test della parafrasi end-to-end** col documento di prova: domanda con parole assenti dal documento → chunk giusto recuperato e risposta grounded con citazioni (criterio di accettazione del milestone)
- [ ] 7.4 Benchmark formale GPU vs CPU sul workload RAG (recupera il benchmark rinviato da M1); annotare gli esiti
- [ ] 7.5 Validare il re-import dalla UI col flusso `.part` + checksum, verificando il checksum a valle (coda di M1)

## 8. Documentazione e chiusura

- [ ] 8.1 Aggiornare `design.md` della change con gli esiti dello spike e delle tarature (Open Questions risolte)
- [ ] 8.2 Aggiornare `CLAUDE.md`/`AGENTS.md` se cambiano comandi, dipendenze o struttura dei package
- [ ] 8.3 Verifica finale: tutti gli scenari delle quattro delta spec coperti; aggiornare la memoria di progetto; pronto per l'eventuale bivio M3 (host MCP)
