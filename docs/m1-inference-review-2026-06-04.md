# Review M1 inferenza on-device - 2026-06-04

Questo documento salva lo stato della review prima di pulire la context window.
Scope: M1 chat locale on-device con Gemma 4 E2B-it via LiteRT-LM. La RAG non è ancora
implementata e non è considerata una mancanza in questa review.

## Stato fix - 2026-06-05

Fix applicate nel repo:

- `InferenceEngine`:
  - `load()`, generazione e auto-unload sono serializzati dallo stesso mutex;
  - il keep-alive viene cancellato durante la generazione e riarmato solo alla fine;
  - `load(path, backend)` e' idempotente su path + backend richiesto;
  - gli errori dello stream vengono loggati e rilanciati;
  - la chat passa `systemInstruction` e `initialMessages` a `ConversationConfig`, poi invia
    solo il nuovo turno utente.
- `Model manager`:
  - import su `.part` con move finale;
  - cleanup del `.part` su errore;
  - stato `READY` basato su dimensione plausibile, non piu' su file non vuoto;
  - `activeModelFile()` rifiuta file parziali.
- CI:
  - JDK 21;
  - step reali `test`, `ktlintCheck`, `lintDebug`.

Validazione locale passata con JDK 21 Homebrew:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew test --stacktrace
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew ktlintCheck --stacktrace
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew lintDebug --stacktrace
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug --stacktrace
```

Resta da validare su Poco:

- avvio reale Gemma 4 E2B-it con config multimodale corrente (`visionBackend=GPU`,
  `audioBackend=CPU`, `maxNumTokens=4000`);
- confronto GPU vs CPU dalle impostazioni, ora che il cambio backend forza davvero la
  ricreazione dell'engine;
- assenza del crash dopo scadenza keep-alive;
- qualita' della chat con `ConversationConfig` strutturata.

## Contesto verificato

- App: `it.netseven.raglocale`, versione `0.1.0`, installata sul Poco.
- Device letto via ADB: `2311DRK48G`, hardware `mt6897` (Poco X6 Pro / MediaTek).
- Modello importato nell'app:
  - path app: `files/models/gemma-4-E2B-it.litertlm`
  - size da device: `2,588,147,712` byte
  - coerente con il modello Gemma 4 E2B-it scaricato/usato in Google AI Edge Gallery.
- Manifest installato: app debuggable, native library OpenCL viste dal package manager:
  `libOpenCL.so`, `libvndksupport.so`, piu' varianti vendor.
- Quindi il problema principale non sembra essere "file modello sbagliato".

## Repo di riferimento

Vedi anche `docs/reference-repos.md`.

- Anti-vocale: https://github.com/RisorseArtificiali/anti-vocale
  - clone locale di lavoro: `/private/tmp/anti-vocale`
- Google AI Edge Gallery: https://github.com/google-ai-edge/gallery
  - clone locale di lavoro: `/private/tmp/ai-edge-gallery`
- LiteRT-LM Kotlin API:
  - https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md

## Evidenze device

Logcat del processo `it.netseven.raglocale`:

```text
E/tflite: Input tensor 11 lacks data
E/litert: [litert_compiled_model.cc:162] Failed to invoke
I/InferenceEngine: Keep-alive scaduto: auto-unload del modello
E/tflite: Input tensor 11 lacks data
F/libc: Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x18
```

Memoria al momento della lettura:

- `TOTAL PSS`: circa `2.38 GB`
- `TOTAL SWAP PSS`: circa `1.20 GB`
- `GL mtrack`: circa `715 MB`
- Il modello era stato scaricato/caricato, poi l'auto-unload ha innescato crash nativo.

## Findings prioritari

### P0 - Keep-alive chiude il motore mentre LiteRT-LM ha ancora lavoro nativo attivo

File: `app/src/main/java/it/netseven/raglocale/inference/InferenceEngine.kt`

Zone:

- `generate()` crea una `Conversation`, colleziona lo stream e chiude la conversation nel
  `finally`.
- `startKeepAlive()` lancia un job indipendente che dopo il delay chiama `unload()`.
- `unloadInternal()` chiude `engine?.close()` senza prendere `generationMutex`.

Rischio:

- Se il timer scade mentre una generazione, una cancellazione o thread nativi LiteRT-LM
  sono ancora vivi, si puo' chiudere l'engine sotto i piedi del runtime.
- Sul device e' stato osservato `Keep-alive scaduto` seguito da `Input tensor 11 lacks data`
  e poi `SIGSEGV`.

Fix suggerito:

- Cancellare il timer a inizio generazione.
- Riarmarlo solo quando la generazione e' davvero finita.
- Proteggere ogni `unload()` con lo stesso lock della generazione, oppure avere un lock
  dedicato engine lifecycle.
- Non chiudere `engine` mentre una `Conversation` e' attiva.

### P0 - Configurazione multimodale sospetta per chat solo testo

File: `app/src/main/java/it/netseven/raglocale/inference/InferenceEngine.kt`

Codice corrente:

```kotlin
EngineConfig(
    modelPath = path,
    backend = backend.toLiteRt(),
    visionBackend = Backend.GPU(),
    audioBackend = Backend.CPU(),
    maxNumTokens = 4000,
    cacheDir = appContext.cacheDir.absolutePath,
)
```

Rischio:

- La chat invia solo `Contents.of(Content.Text(prompt))`.
- Il log nativo ripete `Input tensor 11 lacks data`, sintomo compatibile con grafo/modality
  che aspetta input non fornito.
- Google AI Edge Gallery abilita `visionBackend` e `audioBackend` in modo condizionale
  in base a `supportImage` / `supportAudio`. Per chat text-only non forza sempre audio/vision.
- Anti-vocale forza `audioBackend = CPU` per il suo caso audio, non per una chat solo testo.

Fix suggerito:

- Allineare la config a Gallery:
  - backend LLM = preferenza utente.
  - `visionBackend` solo se il task supporta immagini.
  - `audioBackend` solo se il task supporta audio.
- Fare un test A/B sul Poco:
  1. text-only con `visionBackend = null`, `audioBackend = null`;
  2. config Gallery per Gemma 4 E2B con support image/audio;
  3. CPU solo testo.
- Loggare esplicitamente `backend`, `visionBackend`, `audioBackend`, `maxNumTokens` a ogni init.

### P1 - Cambio backend ignorato se il modello e' gia' carico

File: `app/src/main/java/it/netseven/raglocale/inference/InferenceEngine.kt`

Codice:

```kotlin
if (_isReady.value && currentModelPath == modelPath) {
    resetKeepAlive()
    return
}
```

Rischio:

- Se l'utente cambia GPU/CPU nelle impostazioni, `load(path, newBackend)` ritorna senza
  ricreare l'engine perche' controlla solo il path.
- Si pensa di testare CPU ma l'app puo' restare sul vecchio backend.

Fix suggerito:

- Includere il backend desiderato nella chiave di idempotenza.
- Separare:
  - `requestedBackend`
  - `effectiveBackend` dopo eventuale fallback.

### P1 - Errori di streaming ingoiati

File: `app/src/main/java/it/netseven/raglocale/inference/InferenceEngine.kt`

Codice:

```kotlin
conversation.sendMessageAsync(...)
    .catch { err -> Log.e(TAG, "Errore streaming", err) }
    .collect { message -> emit(message.toString()) }
```

Rischio:

- `.catch` logga ma completa il Flow.
- La UI puo' vedere una risposta vuota/parziale come se fosse conclusa bene.
- I problemi nativi diventano difficili da distinguere da "modello ha risposto male".

Fix suggerito:

- Dopo il log, rilanciare l'errore (`throw err`) o emettere uno stato tipizzato di errore.
- Non mascherare errori LiteRT-LM in M1: meglio fallire esplicitamente.

### P1 - Prompt/chat non usa la struttura ufficiale di ConversationConfig

File:

- `app/src/main/java/it/netseven/raglocale/chat/ChatContextBuilder.kt`
- `app/src/main/java/it/netseven/raglocale/chat/ChatViewModel.kt`

Codice corrente:

- Tutta la cronologia viene serializzata come testo con tag `Utente:` / `Assistente:`.
- Poi viene inviata come unico `Content.Text`.

Rischio:

- Il `.litertlm` include gia' un chat template interno.
- Gallery usa `systemInstruction` e `initialMessages` su `ConversationConfig`, poi invia
  solo il nuovo turno utente.
- Il formato manuale puo' interagire male con il template interno, soprattutto con Gemma 4.

Fix suggerito:

- Cambiare API interna:
  - `InferenceEngine.generate(...)` dovrebbe accettare system prompt, history strutturata
    e user message, oppure una `ConversationRequest`.
  - creare `ConversationConfig(systemInstruction = ..., initialMessages = ...)`;
  - inviare solo `Content.Text(userMessage)`.
- Questa forma sara' anche migliore per la futura RAG: il prompt grounded potra' diventare
  system/extra context esplicito invece di una stringa fragile.

### P2 - Import modello non atomico e validazione troppo debole

File:

- `app/src/main/java/it/netseven/raglocale/modelmanager/ModelRepository.kt`
- `app/src/main/java/it/netseven/raglocale/modelmanager/ModelInfo.kt`

Problemi:

- `ModelStatus.READY` se `file.exists() && fileSizeBytes > 0`.
- `importFromUri()` copia direttamente sul file finale.
- Nessun controllo estensione, dimensione minima/attesa, checksum, o cleanup su fallimento.

Rischio:

- Un import interrotto puo' lasciare file parziale marcato pronto.
- Un file sbagliato ma non vuoto viene passato a LiteRT-LM.

Fix suggerito:

- Copia su `.part`, poi rename atomico.
- Validare `.litertlm`.
- Considerare `sizeBytes` con tolleranza o checksum quando disponibile.
- Su errore cancellare `.part` e non toccare il file pronto precedente.

### P2 - CI non esegue nulla e usa JDK sbagliata

File: `.github/workflows/ci.yml`

Problemi:

- Setup `JDK 17`, mentre il progetto richiede JDK 21.
- Step test/lint sono `echo TODO`.

Fix suggerito:

- Passare a `java-version: "21"`.
- Eseguire almeno:
  - `./gradlew test`
  - `./gradlew ktlintCheck`
  - `./gradlew lintDebug`

## Ambiente locale desktop

Su questa macchina la verifica Gradle non e' partita:

```text
java version "1.8.0_341"
Could not resolve com.android.tools.build:gradle:8.10.0
Dependency requires at least JVM runtime version 11. This build uses a Java 8 JVM.
```

Nota:

- `/usr/libexec/java_home -v 21` sta ricadendo su Java 8.
- Prima di validare build/test locali serve installare/configurare una JDK 21 reale.

## Ordine consigliato per le fix

1. Rendere sicuro il lifecycle di `InferenceEngine`: lock/unload/keep-alive/generation.
2. Smettere di ingoiare errori di streaming.
3. Correggere idempotenza backend.
4. Allineare `EngineConfig` al pattern Gallery e testare text-only su Poco.
5. Passare da prompt stringato a `ConversationConfig(systemInstruction, initialMessages)`.
6. Rafforzare import/status modello.
7. Sistemare CI e JDK 21.

## Nota su modifiche locali al momento della review

Prima della review era gia' presente una modifica non committata in:

- `app/src/main/java/it/netseven/raglocale/inference/InferenceEngine.kt`

La modifica aggiungeva `visionBackend`, `audioBackend` e `maxNumTokens`. Va trattata come
contesto esistente e non revertita alla cieca.

## Stato fix - 2026-06-05

Applicato:

- `InferenceEngine` serializza load/generate/unload, chiude `Conversation`, usa callback API
  LiteRT-LM, e invia richiesta strutturata (`ConversationConfig` + `Content.Text`).
- L'estrazione risposta usa `Message.contents`/`Content.Text` invece di affidarsi a `toString()`.
- `ChatViewModel` interrompe output palesemente corrotto o generazioni oltre timeout, scaricando
  il modello e mostrando un messaggio di errore invece di continuare a streammare caratteri casuali.
- `ModelRepository` importa su `.part`, valida dimensione plausibile e fa move atomico.
- CI aggiornata a JDK 21 con `test`, `ktlintCheck`, `lintDebug`.
- LiteRT-LM aggiornato a `0.12.0`.

Validato:

- `./gradlew test --stacktrace`
- `./gradlew ktlintCheck --stacktrace`
- `./gradlew assembleDebug --stacktrace`
- `./gradlew lintDebug --stacktrace`
- APK debug reinstallato sul Poco; cache LiteRT-LM rimosse lasciando intatto
  `files/models/gemma-4-E2B-it.litertlm`.

Residuo critico:

- Il modello `gemma-4-E2B-it.litertlm` continua a fallire dentro LiteRT-LM con
  `Input tensor 11 lacks data` / `Failed to invoke` anche in smoke minimale:
  text-only GPU, text-only CPU, speculative on/off, e placeholder immagine+WAV valido.
- Quindi il problema residuo sembra compatibilita' modello/runtime/device, non UI Compose,
  prompt builder, callback parsing, backend GPU, cache o input multimodale mancante.
- Prossimo passo reale: provare un `.litertlm` noto compatibile nella Gallery AI Edge sullo
  stesso device, oppure importare un modello alternativo e bloccare a monte questo artifact.

## Root cause trovata - 2026-06-05

L'ipotesi "compatibilità modello/runtime/device" è **smentita**: il file importato
nell'app era **corrotto**. Verifica md5 a tre vie sul device (stessa dimensione,
2.588.147.712 byte, per tutte e tre le copie):

| Copia | Percorso | MD5 |
|---|---|---|
| Originale Gallery | `Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/6e5c4f1e.../` | `1b8446203a216cfd31f6a2a22f75e5e5` |
| Staging Downloads | `/sdcard/Download/gemma-4-E2B-it.litertlm` | `1b8446203a216cfd31f6a2a22f75e5e5` |
| Importata nell'app | `files/models/gemma-4-E2B-it.litertlm` | `8c4d58f03ab75e5f2ee6cfd6d8a3b4bb` ❌ |

Evidenze a supporto:

- La Gallery esegue lo **stesso identico file** (md5 verificato) sullo stesso Poco,
  con litertlm **0.11.0** (versione più vecchia della nostra 0.12.0): le sue cache
  mldrift/xnnpack del 29/05 lo provano.
- La `mldrift_program_cache.bin` generata dalla nostra app ha dimensione identica a
  quella della Gallery (12.604.960 byte): il grafo compilava, erano i **pesi** corrotti,
  per questo il fallimento arrivava solo all'invoke.
- La copia corrotta era stata importata il 04/06 alle 20:48 con il codice di import
  **pre-fix** (copia diretta sul file finale, nessuna validazione) e mai re-importata.

Lezione: la validazione `READY` su **sola dimensione non basta** — questo file corrotto
la passava (dimensione esatta al byte). Valutare checksum all'import, o almeno loggare
l'md5 per diagnosi.

Fix applicata sul device (05/06):

- copia sana riportata nell'app via `cat | run-as it.netseven.raglocale` (on-device,
  niente WiFi), md5 verificato = `1b8446...`;
- cache LiteRT orfane rimosse (~800 MB liberati);
- engine reinizializzato correttamente su CPU (init ~30 s senza cache, config text-only
  `visionBackend=null, audioBackend=null`), zero `lacks data` nei log al load.

## Validazione finale - 2026-06-05

Smoke end-to-end **superato su entrambi i backend** con il file sano:

- **UI reale (CPU)**: messaggio inviato dalla chat, risposta coerente e completa
  ("Ciao, sono un assistente virtuale addestrato da Google, ..."), reload del modello
  dalla cache in ~1 s, zero errori nativi nel logcat.
- **`InferenceSmokeTest` strumentato (CPU + GPU)**: `OK (2 tests)` in 19,8 s totali.
  Init CPU 5,7 s, init GPU 8,6 s, generazione valida su entrambi (assertion anti-gibberish).
  Anche il sintomo GPU (caratteri casuali / SIGSEGV all'init) è sparito col file integro.

Il test vive in `app/src/androidTest/.../InferenceSmokeTest.kt` e resta come guardia di
regressione per import/runtime (salta, non fallisce, se il modello non è importato).

Note operative device (Poco X6 Pro, MIUI/HyperOS):

- `adb shell input` richiede il toggle "Debug USB (impostazioni di sicurezza)";
  l'install via adb richiede "Installa tramite USB" (altrimenti
  `INSTALL_FAILED_USER_RESTRICTED`).
- **Attenzione:** `./gradlew connectedAndroidTest` **disinstalla l'app a fine run**
  (anche in caso di install fallita), azzerando i dati e quindi il modello importato.
  Sul device di sviluppo preferire l'instrumentation manuale, che non fa cleanup:
  `adb install -r -t app-debug-androidTest.apk` poi
  `adb shell am instrument -w -e class it.netseven.raglocale.inference.InferenceSmokeTest it.netseven.raglocale.test/androidx.test.runner.AndroidJUnitRunner`.
- Ripristino rapido del modello dopo un wipe dei dati:
  `adb shell 'run-as it.netseven.raglocale mkdir -p files/models; cat /sdcard/Download/gemma-4-E2B-it.litertlm | run-as it.netseven.raglocale sh -c "cat > files/models/gemma-4-E2B-it.litertlm"'`
  e verifica md5 = `1b8446203a216cfd31f6a2a22f75e5e5`.

Rimasto aperto (non bloccante):

- Re-import dalla UI col nuovo codice (`.part` + move atomico) ancora da validare con
  il file reale: dopo l'import, verificare l'md5 della copia in `files/models/`.
- Valutare un checksum reale all'import (o almeno log dell'md5), visto che la
  validazione su sola dimensione non intercetta questa classe di corruzione.
