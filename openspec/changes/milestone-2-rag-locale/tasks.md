# Tasks: milestone-2-rag-locale

## 1. Spike embedder (de-risk del rischio #1 — primo task assoluto)

- [ ] 1.1 Ricerca dell'artefatto EmbeddingGemma per on-device: fonte (HF `litert-community`?), formato (`.tflite`/`.litertlm`), dimensione embedding, tokenizer incluso, eventuali prefissi di task per il retrieval; annotare le coordinate
- [ ] 1.2 Far girare l'embedder sul Poco nel nostro stack provando i candidati in ordine (D2): LiteRT puro → modulo embedder di `localagents-rag` standalone → fallback Gecko; misurare latenza per chunk e RAM
- [ ] 1.3 Verificare la qualità degli embedding su frasi di prova in italiano (similarità sensata, inclusa una coppia di parafrasi) e se i vettori sono normalizzati (cosine vs dot product)
- [ ] 1.4 Annotare l'esito dello spike in `design.md` (decisione D2 sciolta, Open Questions chiuse); se nessun candidato è praticabile, fermarsi e ridiscutere il design

## 2. Pipeline pura (Kotlin JVM, senza dipendenze Android)

- [ ] 2.1 `Chunker`: size/overlap configurabili, tagli preferenziali ai confini di frase/paragrafo, casi limite (testo corto, overlap ≥ size) + unit test
- [ ] 2.2 Ranking cosine top-K in Kotlin puro + unit test con vettori deterministici
- [ ] 2.3 Interfaccia tool-shaped della retrieval (`cerca(query, topK) → chunk con testo/score/riferimento`) e tipi condivisi (`NormalizedText`, chunk) — contratto stabile per UI, PromptBuilder e futuro M3
- [ ] 2.4 `PromptBuilder` grounded: chunk numerati + istruzione di rispondere solo dal contesto citando i numeri; parsing dei marcatori `[n]` dalla risposta + unit test (incluso il caso senza marcatori validi)

## 3. Vector store e indicizzazione

- [ ] 3.1 `SqliteVectorStore`: schema persistente (documento, chunk, embedding BLOB, metadati incluso l'embedder usato per l'indice)
- [ ] 3.2 Implementazione della ricerca: embedding della query → scan in memoria + cosine top-K (D3)
- [ ] 3.3 Coerenza embedder indice/query: rilevare il mismatch e proporre la re-indicizzazione invece di restituire risultati incoerenti
- [ ] 3.4 Unit test dello store: round-trip di persistenza e ricerca con embedding finti deterministici

## 4. Ingestion (staging per sorgente — D9)

- [ ] 4.1 `TextFileSource` (.txt/.md via SAF) → `NormalizedText`; limiti documento (vuoto, troppo grande) con messaggi chiari
- [ ] 4.2 Pipeline di ingestion end-to-end con progresso (chunk N/M) e indicizzazione persistente — esercitata sulla sola sorgente testo
- [ ] 4.3 `PdfSource` con PdfBox-Android; rilevamento del PDF senza layer testo (OCR fuori scope, messaggio chiaro)
- [ ] 4.4 `UrlSource` con Jsoup + Readability4J; fallback al testo grezzo se Readability fallisce; errori rete chiari senza stato parziale nell'indice
- [ ] 4.5 Unit test dell'estrazione `NormalizedText` per le tre sorgenti (fixture PDF e HTML di esempio)

## 5. Model manager esteso (embedder)

- [ ] 5.1 Tipo di modello nel catalogo (LLM | embedder): EmbeddingGemma default embedder, Gecko come alternativa, metadati e stato per tipo
- [ ] 5.2 Selezione dell'embedder attivo (persistita) e rimozione, in analogia col LLM attivo
- [ ] 5.3 Generalizzare l'import da file (`.part` + move atomico + checksum, scarto su mismatch) a entrambi i tipi di modello
- [ ] 5.4 Unit test: macchina a stati per tipo, import con checksum non corrispondente

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
