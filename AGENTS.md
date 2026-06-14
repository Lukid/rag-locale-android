# RAG locale Android — Istruzioni per agenti

> File canonico delle istruzioni di progetto (formato AGENTS.md, portabile).
> Claude Code lo legge tramite il symlink `CLAUDE.md`; Codex/opencode/altri lo leggono nativamente.

## Panoramica

Demo didattica: una **pipeline RAG semantica dense-only che gira interamente on-device**
su Android (nessuna API cloud per l'inferenza, il dato privato non lascia il telefono).
App nativa **Kotlin** su **Gradle**, fondata su **AI Edge RAG SDK**
(`com.google.ai.edge.localagents:localagents-rag`).

Pipeline: `ingestion → chunking → embedding → cosine top-K → risposta grounded con citazioni`.
Modelli locali: LLM **Gemma 4 E2B** (fallback Gemma 3n), embedder **EmbeddingGemma** (fallback Gecko).

Design completo: [`docs/superpowers/specs/2026-05-29-rag-locale-android-design.md`](docs/superpowers/specs/2026-05-29-rag-locale-android-design.md).
È il documento di riferimento per scope, vincoli, architettura e rischi. Leggilo prima di implementare.

## Comandi

> **Toolchain richiesta:** **JDK 21** (es. Homebrew `openjdk@21`) e **Android SDK** con
> `platforms;android-36` + `build-tools;36.0.0`. Imposta `JAVA_HOME` su una JDK 21 prima
> di invocare `./gradlew` (la JDK di sistema potrebbe essere più vecchia), e indica l'SDK
> in `local.properties` (`sdk.dir=...`, non committato). Esempio:
> `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew assembleDebug`.

| Scopo | Comando |
|---|---|
| Build (debug) | `./gradlew assembleDebug` |
| Test unitari (JVM) | `./gradlew test` (o `./gradlew :app:testDebugUnitTest`) |
| Test strumentati (su device/emulatore) | `./gradlew connectedAndroidTest` |
| Lint Android | `./gradlew lintDebug` (o `./gradlew lint`) |
| Lint Kotlin (stile) | `./gradlew ktlintCheck` |
| Format | `./gradlew ktlintFormat` |
| Pulizia | `./gradlew clean` |

Test manuale su device (il **Poco X6 Pro 5G** è il pavimento hardware): download/import modelli,
GPU vs CPU fallback, latenza, memoria, e — quando il RAG ci sarà — il "test della parafrasi"
end-to-end (vedi spec). Per lo sviluppo si può `adb push` un `.litertlm` e importarlo dal
Model manager (vedi design D8).

## Struttura

```
app/                  # modulo applicazione Android (da creare)
  src/main/           # codice Kotlin + risorse
  src/test/           # unit test (Chunker, ranking cosine, PromptBuilder, estrazione testo)
  src/androidTest/    # test strumentati on-device
docs/                 # design spec e documentazione
  superpowers/specs/  # spec approvate
specs/                # template per nuove spec di task
```

Componenti a responsabilità singola (dalla spec): `DocumentSource` (3 sorgenti: testo/PDF/URL),
`Chunker`, `Embedder`, `SqliteVectorStore`, `PromptBuilder`+Generator, UI didattica, Model manager.
Confine chiave: **ogni sorgente produce `NormalizedText`**; da lì la pipeline è indipendente dall'origine.

## Stile del codice

- **Kotlin** idiomatico; segui le convenzioni ufficiali Kotlin/Android.
- Formattazione e lint applicati via **ktlint** (vedi `.pre-commit-config.yaml`).
- Una classe = una responsabilità; mantieni testabili i componenti puri (Chunker, ranking) senza dipendenze Android.
- Nomi e commenti in italiano, coerenti con la spec esistente.

## Test

- **Unit** (JVM, senza device): `Chunker` (size/overlap/casi limite), ranking cosine,
  estrazione `NormalizedText` per ciascuna sorgente, formato `PromptBuilder`.
- **Strumentati/manuali** (sul device): inferenza, download modelli, fallback GPU→CPU, memoria.
- Aggiungi i test insieme al codice; preferisci unit test deterministici per la logica della pipeline.

## Sicurezza e comandi sicuri

- **Nessun segreto nel repo.** Chiavi di firma, keystore e credenziali stanno in `local.properties`
  o nei secret di CI — mai committati. Vedi `.gitignore` e `.env.example`. Per il login HuggingFace
  in-app (download dei modelli gated) serve un Client ID OAuth in `local.properties`
  (`hfOauthClientId`): istruzioni in [`docs/oauth-huggingface-setup.md`](docs/oauth-huggingface-setup.md).
- Il dato dell'utente (documenti ingeriti, modelli scaricati) resta on-device per design: non
  introdurre chiamate di rete verso servizi di inferenza. L'unica rete prevista è `UrlSource`
  (fetch di una pagina su richiesta dell'utente) e il download dei modelli dal Model manager.
- **Sicuri da eseguire senza conferma:** `./gradlew test`, `./gradlew lint`, `./gradlew ktlintCheck`,
  build di debug, comandi git di sola lettura.
- **Richiedono conferma/contesto:** firma/release, push, modifiche a CI, comandi che toccano la rete
  o installano APK su un device. Vedi [`docs/agent-execution.md`](docs/agent-execution.md) per la
  policy di esecuzione e le opzioni di sandbox.
