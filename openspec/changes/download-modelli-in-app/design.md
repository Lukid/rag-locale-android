## Context

Il Model manager acquisisce oggi i modelli **solo per import da file**: a valle di un move
atomico nello storage interno, `ModelRepository` espone `activeModelFile`/`activeEmbedderFiles`
e tutto il resto (retrieval, chat) è indipendente dall'origine del file. L'import esiste, ma per
una demo a utenti finali è inusabile: l'LLM pesa 2,6 GB e durante la copia non c'è progresso, né
una distinzione visiva tra "presente / pronto / attivo".

Il Milestone 1 aveva **rinviato** (D8) il download in-app; lo spec `model-manager` lo documenta
come comportamento atteso ma non implementato. Questa change lo realizza. Esiste
un'implementazione di riferimento sullo **stesso stack LiteRT-LM**, l'app **anti-vocale**
(`RisorseArtificiali/anti-vocale`, MIT), da cui si portano quasi 1:1 motore di download, OAuth
HuggingFace e pattern di card. Design completo di riferimento (fatti verificati su HF il
2026-06-13): `docs/superpowers/specs/2026-06-13-download-modelli-in-app-design.md`.

Vincoli di progetto: nessun segreto nel repo; il dato privato dell'utente resta on-device;
l'unica rete consentita è il fetch dei modelli (già previsto dalla security policy in `AGENTS.md`).
Toolchain JDK 21 + Android SDK 36; lint Android (lintDebug) fatale su BOM; test on-device via
`am instrument` (mai `connectedAndroidTest` sul Poco: fa wipe).

## Goals / Non-Goals

**Goals:**
- Scaricare LLM ed embedder con un tap, con progresso reale (%, byte, velocità, ETA), ripresa e
  stati leggibili, finendo nello **stesso staging dell'import** (move atomico + verifica md5).
- Auth a runtime "public-first": l'LLM (pubblico, file pesante) scarica a zero attriti; il login
  HuggingFace serve solo per l'embedder gated.
- Mantenere puri e unit-testabili in JVM i pezzi di logica (offset di ripresa, parsing
  `Content-Range`, decisione 200-vs-206, macchina stati, token manager, derivazione stato-card).

**Non-Goals:**
- Niente **foreground service**: il download è una coroutine; se l'app va in background si ferma e
  riprende dal `.tmp` alla riapertura.
- Niente download in background, niente benchmark in-app (resta `RagBenchmarkTest` strumentato),
  niente OCR, niente modelli oltre al catalogo.
- Nessun cambiamento a valle del move atomico (retrieval/chat invariati).

## Decisions

### D1 — Auth "public-first" decisa a runtime con un HEAD
Un solo percorso di download: **HEAD** sull'URL del file; `200` → pubblico, scarica senza token;
`401`/`403` → serve il token HF. Così l'LLM (non gated) non chiede mai login, e il login emerge
solo quando serve (embedder).
*Alternative considerate:* (a) chiedere sempre il login → attrito inutile sul caso comune;
(b) marcare staticamente "gated" nel catalogo e saltare l'HEAD → fragile se HF cambia lo stato del
repo. Il flag `gated` resta nel catalogo solo come **hint UI** (badge "richiede login"), non come
gate del flusso. Si tiene anche l'HEAD per leggere `Content-Length`/`Accept-Ranges`.

### D2 — Motore di download a coroutine con resume, niente foreground service
`ResumeDownloadHelper.downloadWithResume(config, onProgress, onStateChange)` gira nello scope del
ViewModel. Range request `Range: bytes=N-`: `206` → parsing `Content-Range` per offset e totale e
**append** sul `.tmp`; `200` (range ignorato dal server) → riparte da zero; sidecar `.size` per
conoscere il totale tra riavvii; rename `.tmp`→target a fine corsa. Retry con backoff (3
tentativi); annullamento via flag cooperativo; `DownloadRateTracker`/`ProgressThrottler` per
velocità/ETA senza martellare la UI.
*Alternative considerate:* `WorkManager`/foreground service per sopravvivere al background →
scartato come YAGNI per una demo che si scarica con l'app aperta; resume+retry è la mitigazione, e
il service è un'aggiunta isolata sopra lo **stesso** motore se in validazione servisse.

### D3 — Verifica md5 esatta + move atomico (riuso staging import)
A fine download si verifica con `ImportVerifier` il **md5 canonico pinnato** nel catalogo (non la
sola dimensione — lezione M1: file di pari size ma md5 diverso = corrotto, "Input tensor lacks
data"); poi move atomico riusando la pipeline di staging dell'import. Nessuno stato parziale entra
nell'indice/attivo finché tutti i file non sono pronti e verificati. L'embedder scarica **due
file** (modello `.tflite` + `sentencepiece.model`): pronto solo quando entrambi verificano.
*Alternative considerate:* fidarsi di `Content-Length` → già fallito in M1.

### D4 — OAuth HuggingFace con AppAuth + Custom Tabs, token cifrato
`HuggingFaceAuthManager` (unico a conoscere AppAuth) fa authorization_code + PKCE S256 con
`prompt=consent` (per ottenere il refresh token), scope `read-repos`, redirect
`it.netseven.raglocale://oauth2callback`. `HuggingFaceTokenManager` (interfaccia + impl) persiste
access/refresh/scadenza **cifrati**, espone `getEffectiveToken()` (refresh se scaduto), `username`,
`logout()`, `isLoggedIn`. L'interfaccia è il **seam** unit-testabile in JVM con un fake.
`HuggingFaceApiClient` valida il token e ricava lo username via userinfo (OkHttp già in progetto).
*Alternative considerate:* incollare manualmente un token HF → peggior UX e gestione del segreto
sul device; WebView custom per OAuth → sconsigliato da HF e meno sicuro di Custom Tabs.

### D5 — Client ID via BuildConfig, degrado con grazia
Il Client ID dell'OAuth app HF sta in `local.properties` (`hfOauthClientId`, non committato),
esposto come `BuildConfig.HF_OAUTH_CLIENT_ID`; `manifestPlaceholders["appAuthRedirectScheme"]`.
`HuggingFaceOAuthConfig.isConfigured()` degrada con grazia: se il Client ID manca, la UI mostra
"login non configurato" e **lascia comunque scaricare i modelli pubblici**. Dipendenza
`net.openid:appauth:0.11.1`.
*Alternative considerate:* committare un client ID condiviso → viola "nessun segreto nel repo" e
lega la demo a un'app HF non controllata dall'utente.

### D6 — UI a card per modello, una capability separata per l'auth
`ModelManagerScreen`/`ViewModel` rifatti attorno a una **card per modello**: icona di stato,
badge ATTIVO, progresso ricco, sezione parziale con Riprendi/Annulla, bottoni per stato
(assente→Scarica; download→Annulla; parziale→Riprendi/Cancella; pronto→Usa/Elimina). Chip HF in
cima (username/Esci oppure Accedi). L'attivazione (`Usa`) resta la `setActive` esistente, distinta
da "pronto". L'auth HF è modellata come **capability separata** (`huggingface-auth`) perché è un
comportamento utente a sé (login/logout/username) riusabile e con confine netto sulla dipendenza
esterna AppAuth.

## Risks / Trade-offs

- **Dipendenza di setup esterna (OAuth app HF)** → confinata in `local.properties`/`BuildConfig`;
  degrado con grazia se assente (i pubblici scaricano lo stesso); istruzioni nella spec e nel README.
- **Download senza foreground service muore in background** → mitigato da resume+retry dal `.tmp`;
  accettato per la demo; il service è un'aggiunta isolata sopra lo stesso motore.
- **Token HF cifrato sul device** → nessun segreto nel repo (Client ID in `local.properties`); il
  token è personale dell'utente.
- **Gemma embedder è gated:auto** → l'utente deve accettare la licenza una volta sul web; lo si
  comunica in chiaro nel flusso di login.
- **Test on-device** → mai `connectedAndroidTest` sul Poco (wipe); usare `am instrument`. La logica
  pura è coperta in JVM, l'IO di rete on-device.

## Migration Plan

1. Aggiungere dipendenza AppAuth e `BuildConfig.HF_OAUTH_CLIENT_ID` (degrada se assente).
2. Estendere il catalogo con `downloadUrl`/`gated`/md5/size reale — additivo, nessuna migrazione dati.
3. Introdurre motore di download + auth dietro le nuove azioni della card; l'import resta invariato
   e riusa la stessa barra di progresso.
4. Rollback: la feature è additiva sopra il confine del move atomico; rimuoverla riporta al solo
   import senza toccare modelli già presenti né l'attivo.

## Open Questions

- Nessuna bloccante. In validazione on-device si conferma se il "no foreground service" è
  accettabile per l'esperienza di download dell'LLM da 2,6 GB; se fastidioso, valutare il service
  come change successiva isolata.
