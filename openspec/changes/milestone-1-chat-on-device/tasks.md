## 1. Scaffolding e fondamenta

- [x] 1.1 Inizializzare il progetto Gradle/Android con Gradle wrapper (`./gradlew`) e modulo `app/`. Toolchain (da anti-vocale): **JDK 21**, **Kotlin 2.1+** (plugin `org.jetbrains.kotlin.plugin.compose`), AGP ~8.10, Gradle ~8.11, compileSdk 36 / targetSdk 35 / **minSdk 26**, Jetpack Compose
- [x] 1.2 Definire la struttura a package per i componenti separati: `modelmanager`, `inference`, `chat`, `ui` (confini puliti per l'innesto del Milestone 2)
- [x] 1.3 Configurare ktlint/detekt e allineare `.pre-commit-config.yaml`; verificare `./gradlew assembleDebug`, `test`, `lint` — _ktlint via plugin Gradle (`.editorconfig`); `assembleDebug`/`test`/`ktlintCheck`/`lintDebug` verdi. detekt rimandato._
- [x] 1.4 Aggiungere `local.properties`/keystore a `.gitignore` se non già coperti; nessun segreto nel repo — _già coperti dal `.gitignore` esistente._
- [x] 1.5 Dipendenze e manifest per LiteRT-LM: `com.google.ai.edge.litertlm:litertlm-android:0.11.0` (+ `com.google.mediapipe:tasks-genai` come fallback text-only), Hilt, DataStore, OkHttp, Coroutines, Compose BOM; nel `AndroidManifest.xml` `<uses-native-library android:name="libOpenCL.so"/>` e `libvndksupport.so` (`required=false`) per la GPU; `packaging { jniLibs { useLegacyPackaging = true } }` + esclusioni META-INF

## 2. Spike di build LiteRT-LM (de-risk D2 / Open Questions)

- [x] 2.1 Far compilare un caricamento minimo con l'API reale (`com.google.ai.edge.litertlm.*`): `Engine(EngineConfig(modelPath, Backend.GPU(), cacheDir))` → `initialize()` — _compila contro litertlm 0.11.0._
- [ ] 2.2 Procurare il `.litertlm` di Gemma 4 E2B: copiarlo dalla Gallery (`adb shell cp …/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/*/gemma-4-E2B-it.litertlm /sdcard/Download/`) e `adb push` nella nostra app — nessun ri-download (D8) — _⏸️ BLOCCATO: richiede il device._
- [ ] 2.3 Eseguire una singola inferenza con `createConversation(...)` → `sendMessageAsync(Contents.of(Content.Text(...))).collect { }`, confermando: **streaming dal Flow**, **`Backend.GPU()` davvero accelerato sulla Mali nella NOSTRA app** (Open Question critica), nessun OOM — _⏸️ BLOCCATO: richiede il device._
- [x] 2.4 Annotare nel design eventuali scostamenti dell'API reale vs. attesa; se la GPU non accelera in casa nostra, decidere CPU vs. tuning manifest — _annotato in `design.md` (nessuno scostamento API); la decisione GPU/CPU dipende dal test 2.3 (device)._

## 3. Inference engine (spec `inference-engine`)

- [x] 3.1 Implementare il manager d'inferenza (singleton Hilt, sul modello di `LlmManager` di anti-vocale): crea `Engine`+`Conversation` LiteRT-LM ed espone `StateFlow<Boolean>` di readiness
- [x] 3.2 Esporre la selezione backend GPU/CPU con default GPU (preferenza persistita)
- [x] 3.3 Implementare il fallback automatico GPU→CPU all'init fallita, con avviso *sfumato* (CPU: più lenta su prefill, più rapida su decode/load)
- [x] 3.4 Ciclo di vita "modello residente": carica una volta, riusa la sessione tra le query (serializzando — LiteRT-LM ammette **una sola conversazione alla volta**), **keep-alive timer con auto-unload** dopo inattività, indicatore di caricamento durante l'init
- [x] 3.5 Unit test della logica di decisione backend/fallback (parte pura, senza dipendenze Android)

## 4. On-device chat (spec `on-device-chat`)

- [x] 4.1 Invio messaggio e risposta **in streaming** token-per-token collezionando il `Flow` di `sendMessageAsync(...)` ed emettendo ogni chunk alla UI
- [x] 4.2 Gestire la cronologia di sessione (solo testo) e includerla nel contesto passato al modello
- [x] 4.3 Applicare il cap configurabile sui token di output (default basso) con interruzione pulita al limite
- [x] 4.4 Implementare l'interruzione manuale della generazione mantenendo il testo prodotto
- [x] 4.5 Unit test della costruzione del contesto di chat (assemblaggio cronologia + nuovo messaggio) e dell'applicazione del cap

## 5. Model manager (spec `model-manager`)

> Staging (da anti-vocale): partire dalla **selezione di un `.litertlm` presente sul device** (+ copia da Gallery / `adb push`); il download HF in-app con OAuth (AppAuth + token cifrato) è la rifinitura successiva.

- [x] 5.1 Implementare il catalogo dei modelli LLM con metadati (nome, dimensione, quantizzazione) e Gemma 4 E2B-it come default
- [ ] 5.2 Implementare il download con progresso e **ripresa** da interruzione (da Hugging Face, `litert-community/gemma-4-E2B-it-litert-lm`) — _⏸️ RINVIATO (staging): M1 usa l'import da file; download in-app = iterazione successiva._
- [x] 5.3 Implementare il check dello spazio di storage prima del download, con blocco e messaggio se insufficiente
- [x] 5.4 Implementare selezione del modello attivo, visualizzazione stato (non scaricato / in download / pronto) e rimozione
- [ ] 5.5 Gestire l'eventuale flusso licenza/token Hugging Face (o documentare il fallback `adb push` per lo sviluppo) — _⏸️ RINVIATO (staging): fallback `adb push`/import documentato (D8); OAuth/token = iterazione successiva._
- [x] 5.6 Unit test della macchina a stati del modello e della logica di check storage

## 6. UI (Jetpack Compose)

- [x] 6.1 Schermata model manager: catalogo, progresso download, stato, selezione, rimozione
- [x] 6.2 Schermata chat: input testo, risposta in streaming, pulsante stop, indicatore di caricamento del modello
- [x] 6.3 Impostazione di selezione backend GPU/CPU e visualizzazione dell'avviso di fallback
- [x] 6.4 Cablare le schermate ai componentei `modelmanager`/`inference`/`chat`

## 7. Test e verifica

- [x] 7.1 Assicurare la copertura unit JVM dei componenti puri (decisione backend, contesto chat, cap token, stato modello, check storage) — _24 unit test verdi._
- [ ] 7.2 Test manuale sul Poco X6 Pro: download modello, chat in streaming, confronto GPU vs CPU, assenza di OOM, latenza coerente con lo spike — _⏸️ BLOCCATO: richiede il device._
- [ ] 7.3 Annotare i risultati del test sul device (verdetto coerente con la memory `spike-gemma4-e2b-poco`) — _⏸️ BLOCCATO: richiede il device._

## 8. Documentazione e chiusura

- [x] 8.1 Aggiornare la tabella *Comandi* di `CLAUDE.md`/`AGENTS.md` con i comandi Gradle reali (rimuovere il TODO-by-stack)
- [x] 8.2 Aggiornare il design con le risposte alle Open Questions emerse durante l'implementazione
- [ ] 8.3 Verifica finale: tutti gli scenari delle spec coperti; pronto per il bivio del Milestone 2 (RAG locale vs MCP host) — _verifica JVM/build completata; gli scenari on-device (streaming reale, GPU, OOM, latenza) restano da validare sul device (7.2/7.3)._
