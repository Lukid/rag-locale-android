## Context

Greenfield: il repo ha il design approvato (`docs/superpowers/specs/2026-05-29-rag-locale-android-design.md`) ma nessun codice Android. Lo spike sul device (memory `spike-gemma4-e2b-poco`) ha già risolto il rischio #1: Gemma 4 E2B-it gira accelerata su GPU Mali del Poco X6 Pro, con numeri reali (GPU: prefill 558 tok/s, decode 9,96 tok/s, TTFT 1,94s su 1024 ctx, init a caldo 8,8s; CPU: prefill 129, decode 13,5, TTFT 8s, init 1,8s).

Questo milestone costruisce le fondamenta: un'app che scarica un modello e ci chatti direttamente, **comune a entrambe le anime** in valutazione (RAG locale vs MCP host, vedi memory `pivot-mcp-host`). Il riferimento di partenza è **Google AI Edge Gallery**, l'app open-source di Google per esattamente questo stack (LiteRT-LM, download modelli, chat on-device).

Vincoli: pavimento hardware Poco X6 Pro (8GB reali, GPU Mali, niente NPU); Kotlin idiomatico; nomi/commenti in italiano; nessun segreto nel repo; l'unica rete è il download dei modelli.

**Implementazione di riferimento:** `RisorseArtificiali/anti-vocale` (MIT) — app on-device reale sullo *stesso stack* (Kotlin/Compose/Hilt + LiteRT-LM per Gemma 4 E2B), **non un fork della Gallery** ma un'app originale che ne riusa i pezzi. La studiamo come modello concreto: risolve gran parte delle nostre Open Questions (coordinate Maven, API, toolchain, distribuzione modello). Vedi memory `reference-anti-vocale`.

## Goals / Non-Goals

**Goals:**
- Progetto Gradle/Android reale e buildabile, con un'architettura a componenti separati che regga l'innesto del Milestone 2 (RAG *o* MCP) senza riscrivere le fondamenta.
- Scaricare/selezionare un modello LLM in-app e chattarci in streaming, su GPU o CPU, sul Poco.
- Trasformare lo spike in codice di produzione: il `ModelLoader` con fallback GPU→CPU è già richiesto dal design.

**Non-Goals:**
- Qualunque cosa RAG (embedder, vector store, cosine, ingestion, citazioni, PromptBuilder grounded).
- MCP host / tool calling.
- Multimodale (immagini/audio della Gallery).
- Misura precisa della RAM e caso "LLM + embedder residenti insieme" (è materia del Milestone 2 / rischio #2).

## Decisions

### D1 — Gallery come mappa, non come territorio (minimal-and-borrow)
Costruiamo un'app minima nostra, leggendo la Gallery per *come* fa download e inferenza e portando solo quei pezzi, invece di forkare-e-sfoltire l'intera app multi-task.
- *Perché:* l'app è didattica e al Milestone 2 dovrà esporre confini puliti (componenti a responsabilità singola). Forkare l'intera Gallery erediterebbe navigazione e task (image/audio/prompt-lab) che dovremmo combattere.
- *Alternativa scartata:* fork-and-strip — più veloce all'APK, ma cementa un'architettura che non è la nostra.
- *Conferma sul campo:* anti-vocale fa esattamente questo — app originale, architettura propria (interfaccia backend + manager singleton + orchestrator, single-activity Compose, Hilt DI, ViewModel per stato modello), che riusa lo stack LiteRT-LM senza forkare la Gallery.

### D2 — Runtime su LiteRT-LM diretto, non MediaPipe LLM Inference
L'inferenza poggia su **LiteRT-LM**. MediaPipe LLM Inference risulta **deprecato** su Android/iOS.
- *Perché:* è la via viva; la Gallery stessa gira su LiteRT-LM (lo spike lo dimostra).
- *Risolto (da anti-vocale):* dipendenza `com.google.ai.edge.litertlm:litertlm-android:0.11.0` (Google Maven). API: `Engine(EngineConfig(modelPath, backend, cacheDir))` → `engine.initialize()` → `engine.createConversation(ConversationConfig(SamplerConfig(topK, topP, temperature)))` → `conversation.sendMessageAsync(Contents.of(Content.Text(prompt)))` che ritorna un **Flow** da collezionare. Artefatto `.litertlm`. Vincoli toolchain: **JDK 21** e **Kotlin 2.1+**.
- *Fallback text-only confermato:* `com.google.mediapipe:tasks-genai` funziona ancora per solo-testo (`LlmInference.createFromOptions(...).generateResponse(...)`), utile come rete di sicurezza per la chat (e si attiva sui file `.task`).
- *Conseguenza a valle:* l'AI Edge RAG SDK storicamente stava su MediaPipe LLM Inference — impatta il rischio #5 del Milestone 2, ma **non** questo milestone.

### D3 — Backend selezionabile, default GPU, fallback GPU→CPU con avviso sfumato
Il backend è esposto all'utente; default GPU; se l'init GPU fallisce → fallback automatico a CPU con avviso.
- *Perché:* lo spike mostra che non c'è un vincitore unico — GPU vince TTFT (la "reattività" da demo), CPU vince decode e caricamento. La scelta ottima dipende dal workload. L'avviso non deve dire "CPU = solo più lento": è più lenta sul prefill ma più veloce su decode/load.
- *Concreto (da anti-vocale):* il backend si imposta in `EngineConfig(backend = Backend.GPU() | Backend.CPU())`. La GPU (Mali/OpenCL) richiede nel `AndroidManifest.xml` `<uses-native-library android:name="libOpenCL.so" android:required="false"/>` (+ `libvndksupport.so`) e `packaging { jniLibs { useLegacyPackaging = true } }`. Pattern di fallback: prova init GPU → su `Exception`/`Error` → CPU (ed eventualmente MediaPipe). NB: anti-vocale forza CPU per affidabilità del *multimodale audio*; noi (solo testo) puntiamo GPU come da spike — da confermare nel build (vedi Open Questions).
- *Rimandato:* selezione adattiva (prompt corto→CPU, contesto grosso→GPU) — over-engineering per ora.

### D4 — Output in streaming obbligatorio
La risposta scorre token-per-token in UI.
- *Perché:* a ~10 tok/s di decode, lo streaming è ciò che rende l'attesa guardabile (l'utente legge mentre scorre). Non è estetica, è mitigazione del decode lento.
- *Concreto (da anti-vocale):* lo streaming è **nativo** — `conversation.sendMessageAsync(...)` ritorna un `Flow`; invece di accumulare in uno `StringBuilder`, emettiamo ogni chunk alla UI man mano che arriva.

### D5 — Modello residente (carica una volta, tieni caldo)
Il modello viene caricato una volta e mantenuto in memoria per la sessione; mai ricaricato per singola query.
- *Perché:* init ~9s su GPU. Ricaricare per query distruggerebbe l'esperienza. Spinner visibile durante il load iniziale.
- *Concreto (da anti-vocale):* manager **singleton** (Hilt) con `StateFlow<Boolean>` di readiness e **keep-alive timer con auto-unload** dopo inattività (default ~5 min) per igiene RAM. Vincolo LiteRT-LM: **una sola conversazione/sessione alla volta** → serializzare gli accessi.

### D6 — Risposte brevi by design (cap sui token di decode)
Cap configurabile sui token di output, con default basso.
- *Perché:* 10 tok/s × N token = attesa. 100-200 token ≈ 10-20s (ok); 500 token ≈ 50s (no). Allineato col grounding conciso che servirà al RAG.

### D7 — UI in Jetpack Compose
- *Perché:* standard Android moderno, coerente con la Gallery, rapido da iterare.

### D8 — Distribuzione modello: download in-app da Hugging Face + `adb push` in sviluppo
Default **Gemma 4 E2B-it** (`litert-community/gemma-4-E2B-it-litert-lm`). Durante lo sviluppo si può `adb push` il file mentre il download in-app matura.
- *Perché:* sblocca i test sul device senza dipendere dal completamento del flusso di download/licenza.
- *Scorciatoia dev (da anti-vocale):* il `.litertlm` di Gemma 4 E2B **già scaricato nella Gallery durante lo spike** si copia da `…/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/*/gemma-4-E2B-it.litertlm` e si fa `adb push` nella nostra app — nessun ri-download.
- *Staging:* anti-vocale ha spedito **senza** download in-app (selezione file + copia/`adb push`), rimandandolo. Possiamo fare lo stesso: M1 minimo = selezione/caricamento di un `.litertlm` presente sul device; il download HF in-app (AppAuth OAuth + token cifrato `androidx.security:security-crypto`) come rifinitura successiva.

## Risks / Trade-offs

- **Maturità/forma dell'API LiteRT-LM su Android (D2)** → spike di build mirato prima di impegnarsi; in caso di attrito, replicare la via della Gallery.
- **Flusso licenza/auth Hugging Face per Gemma** → potrebbe servire token HF + accettazione licenza in-app; mitigazione per lo sviluppo: `adb push` del file (D8).
- **Decode ~10 tok/s** → streaming (D4) + cap risposte brevi (D6).
- **Init ~9s su GPU** → spinner + modello residente (D5).
- **Minimal-and-borrow è più lavoro iniziale del fork** → mitigato leggendo il codice della Gallery come mappa (D1).
- **Device sotto il target ufficiale dell'SDK** → accettato; lo spike ha già provato la fattibilità.
- **RAM con futuro embedder** → fuori scope qui; segnato per il Milestone 2 (rischio #2).

## Migration Plan

Greenfield: nessuna migrazione. Deploy = build debug installata sul device (`./gradlew assembleDebug` + install su Poco). Rollback non applicabile. Una volta esistente il Gradle wrapper, aggiornare la tabella *Comandi* di `CLAUDE.md`/`AGENTS.md` (oggi TODO-by-stack).

## Open Questions

- ~~Superficie API LiteRT-LM e formato artefatto~~ → **risolto** da anti-vocale (vedi D2): `litertlm-android:0.11.0`, API `Engine`/`Conversation`/`Contents`, artefatto `.litertlm`, toolchain JDK 21 + Kotlin 2.1+.
- ~~Il download in-app richiede token/licenza HF? Quale UX?~~ → **pattern noto** (AppAuth OAuth + token cifrato), ma **rimandabile**: M1 può partire con selezione file + copia da Gallery / `adb push` (vedi D8 staging).
- **`Backend.GPU()` di LiteRT-LM accelera davvero sulla Mali del Poco *dentro la nostra app*?** Lo spike era nella Gallery; anti-vocale girava su CPU (per l'audio). Da confermare nel primo build con le dichiarazioni `<uses-native-library>` (task 2). È l'unica vera incognita rimasta delle fondamenta.
- Soglie del check storage (spazio minimo libero) e spec minime device da comunicare all'utente — ancora aperta.
- Selezione backend: default a livello app o per-sessione? (adattiva rimandata).

## Note di implementazione (M1 — codice scaffoldato)

Aggiornamento a valle dell'implementazione delle fondamenta (codice in `app/`, build verde).

**Stack confermato (vedi memory `stack-native-kotlin-litertlm`).** Dopo aver valutato Flutter+LiteRT-LM
(supporto Flutter *community/early* dal 0.12.0) e **Cactus Compute** (cross-platform ma formato
proprietario e GPU-Android non confermata), si è confermato **Kotlin nativo + LiteRT-LM, Android-first**.
iOS resta una superficie Swift differita dello *stesso* motore (stesso `.litertlm`).

**API LiteRT-LM (task 2.1 / 2.4 — scostamenti vs attesa): nessuno.** Il codice compila contro
`com.google.ai.edge.litertlm:litertlm-android:0.11.0` con esattamente la superficie attesa dalla
reference: `Engine(EngineConfig(modelPath, backend = Backend.GPU()|CPU(), cacheDir))` → `initialize()`
→ `createConversation(ConversationConfig(SamplerConfig(...)))` → `sendMessageAsync(Contents.of(Content.Text()))`
che ritorna un `Flow` collezionato in streaming. `audioBackend` è opzionale (lo omettiamo: solo testo).

**Toolchain (task 1.x).** JDK 21 + Android SDK `platforms;android-36` / `build-tools;36.0.0`; AGP 8.10,
Kotlin 2.2, Gradle 8.11.1; `useLegacyPackaging` + `<uses-native-library libOpenCL.so/libvndksupport.so>`
nel manifest (per la GPU). ktlint configurato (`.editorconfig`): disattivate le regole `annotation`/
`function-signature`/`class-signature` (conflitto con i costruttori Hilt) e `function-naming` esentata
per i `@Composable`. `./gradlew assembleDebug`, `test` (24 unit test verdi), `ktlintCheck`, `lintDebug`: OK.

**Open Question critica ancora aperta — accelerazione GPU sulla Mali nella nostra app.** Il codice
*dichiara* GPU di default con fallback CPU, ma **se la GPU acceleri davvero nella nostra app sul Poco**
non è verificabile senza device. Resta da fare con `adb push` del `.litertlm` + import dal Model manager
(task 2.2/2.3, 7.2/7.3). Idem: streaming reale dal `Flow`, assenza di OOM, latenza coerente con lo spike.

**Staging Model manager (D8).** Implementati: catalogo (Gemma 4 E2B default), import file `.litertlm`,
check storage, selezione attivo, rimozione. **Rinviati** (per scelta): download HF con progresso/ripresa
e flusso OAuth/token (task 5.2/5.5).
