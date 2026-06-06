## Why

Il progetto ha un design approvato ma zero codice: nessun progetto Gradle/Android scaffoldato. Il rischio più alto del design (rischio #1: accelerazione GPU Mali sul Poco X6 Pro) è già stato risolto in positivo da uno spike sul device — Gemma 4 E2B-it gira accelerata su GPU. Serve ora la **prima fetta di prodotto, costruibile e testabile in mano**, che trasformi quello spike in fondamenta vere.

Questa fetta — un'app che **scarica un modello e ci chatti direttamente** — è anche **terreno comune** alle due direzioni in valutazione (RAG locale puro vs. chatbot come MCP host): qualunque anima vinca al Milestone 2, l'app deve prima saper gestire i modelli e dialogare con l'LLM on-device. Costruirla ora sblocca tutto il resto senza impegnare il bivio.

## What Changes

- Scaffolding del progetto Android nativo Kotlin/Gradle (Gradle wrapper + modulo `app/`), aggiornando di conseguenza la sezione *Comandi* di `CLAUDE.md`/`AGENTS.md` (oggi marcata TODO-by-stack).
- Integrazione del runtime di inferenza on-device su **LiteRT-LM** (verificando in build che la via diretta sia quella corretta, dato che MediaPipe LLM Inference risulta deprecato — vedi design.md).
- **Model manager** in-app (stile AI Edge Gallery): download, selezione, stato e check spazio per i modelli LLM. Default **Gemma 4 E2B-it** (`litert-community/gemma-4-E2B-it-litert-lm`).
- **Caricamento modello** con selezione backend **GPU/CPU**, fallback automatico GPU→CPU con avviso *sfumato* (la CPU è più lenta sul prefill ma più veloce su decode/load — non è un mero ripiego), e modello tenuto residente.
- **Chat diretta** col modello locale, **solo testo**, con **output in streaming** (obbligatorio per rendere guardabili i ~10 tok/s di decode misurati).
- Unit test JVM per la logica pura disponibile a questo stadio (es. costruzione del prompt di chat semplice, stato del model manager) e nota dei test manuali sul device.

Esplicitamente **fuori scope** (rimandati a milestone successivi): RAG, embedder, vector store, ricerca cosine, le 3 sorgenti di ingestion, UI didattica chunk/score/citazioni, PromptBuilder grounded, MCP host / tool calling, multimodale (immagini/audio).

## Capabilities

### New Capabilities
- `model-manager`: scoperta, download (con progresso e ripresa), selezione, stato e rimozione dei modelli LLM locali; check dello spazio di storage. Limitato ai modelli LLM in questo milestone (nessun embedder).
- `inference-engine`: caricamento di un modello selezionato in una sessione eseguibile su LiteRT-LM; selezione del backend GPU/CPU con fallback automatico + avviso; ciclo di vita del modello (carica una volta, residente, mai per query).
- `on-device-chat`: sessione conversazionale solo-testo sopra l'inference-engine; invio messaggio, risposta in streaming token-per-token, cronologia di sessione, cap configurabile sui token di output.

### Modified Capabilities
<!-- Nessuna: openspec/specs/ è vuoto (greenfield). -->

## Impact

- **Codice/struttura:** nuovo modulo `app/` (Kotlin + Compose), Gradle wrapper, `app/src/test/` per gli unit test. Prima base reale dell'architettura.
- **Dipendenze:** runtime LiteRT-LM per l'inferenza on-device; librerie download/storage; Jetpack Compose per la UI. (AI Edge RAG SDK e embedder NON entrano in questo milestone.)
- **Documentazione:** aggiornare la tabella *Comandi* di `CLAUDE.md`/`AGENTS.md` con i comandi Gradle reali una volta esistente il wrapper.
- **Device/manuale:** test su Poco X6 Pro 5G (pavimento hardware): download modello, GPU vs CPU, latenza, memoria, assenza di OOM. Il file modello può essere `adb push` durante lo sviluppo prima che il download in-app sia completo.
- **Sicurezza:** nessun segreto nel repo; l'unica rete prevista è il download dei modelli dal model manager (coerente con la policy di `CLAUDE.md`).
