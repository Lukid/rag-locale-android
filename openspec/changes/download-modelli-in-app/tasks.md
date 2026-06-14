# Tasks: download-modelli-in-app

> Riferimenti: `proposal.md`, `design.md`, e il design approvato
> `docs/superpowers/specs/2026-06-13-download-modelli-in-app-design.md`.
> Implementazione di riferimento sullo stesso stack: anti-vocale (`RisorseArtificiali/anti-vocale`).
> Confine invariato: a valle del move atomico, retrieval e chat non cambiano.
> On-device sul Poco **solo** con `am instrument` (mai `connectedAndroidTest`: wipe).

## 1. Setup dipendenze e configurazione OAuth (degrado con grazia)

- [x] 1.1 Aggiungere la dipendenza `net.openid:appauth:0.11.1`; verificare che `INTERNET` sia già nel manifest (lo è) e che OkHttp sia disponibile — _✅ aggiunte `net.openid:appauth:0.11.1` e `androidx.security:security-crypto:1.1.0-alpha06` (token cifrato, serve al gruppo 5); OkHttp e `INTERNET` già presenti._
- [x] 1.2 Leggere `hfOauthClientId` da `local.properties` (non committato) ed esporlo come `BuildConfig.HF_OAUTH_CLIENT_ID`; impostare `manifestPlaceholders["appAuthRedirectScheme"] = "it.netseven.raglocale"`; documentare la chiave in `.env.example`/README — _✅ lettura via `Properties` da `local.properties` (vuoto se assente), `buildConfigField` + `manifestPlaceholders`; chiave documentata in `.env.example`._
- [x] 1.3 Build di debug verde con e senza `hfOauthClientId` valorizzato (il caso "assente" deve compilare e degradare, non rompere) — _✅ `assembleDebug` verde nel caso "assente" (stato attuale di `local.properties`); il caso "presente" è per costruzione (interpolazione del valore nel `buildConfigField`)._

## 2. Catalogo — metadati di download (solo dati)

- [x] 2.1 Estendere `ModelInfo`/`CompanionArtifact` con `downloadUrl: String?` e `gated: Boolean` — _✅ aggiunti i campi + proprietà derivata `scaricabile` (richiede url su modello e companion)._
- [x] 2.2 In `ModelCatalog` pinnare gli md5 canonici e gli URL verificati: LLM `gemma-4-E2B-it.litertlm` (non gated, md5 `1b8446203a216cfd31f6a2a22f75e5e5`, `sizeBytes = 2_588_147_712`), embedder `embeddinggemma-300M_seq512_mixed-precision.tflite` (gated, md5 `edd86dab69e9333794ed983b4ab6d0d3`) + tokenizer `sentencepiece.model` (md5 `b0cab25d6777ffdf26856aaf6316fbbc`) — _✅ helper `hfUrl(repo, file)`; LLM con size/md5 reali e `gated=false`, embedder `gated=true` con url su modello e tokenizer; Gecko resta senza url (non scaricabile)._
- [x] 2.3 Aggiornare/estendere `ModelCatalogTest`: presenza di `downloadUrl`/`gated`/md5 sui default, size reale dell'LLM, l'embedder marcato `gated` — _✅ 3 test nuovi (LLM pubblico url+md5+size, embedder gated a due url, Gecko non scaricabile); suite catalogo verde._

## 3. Logica pura del download (Kotlin JVM, TDD prima dell'IO)

- [x] 3.1 `DownloadState` (sealed): `Idle`, `CheckingAccess`, `Connecting`, `Downloading(byte, totale, %, rate, eta)`, `Retrying`, `PartiallyDownloaded`, `Complete`, `Error`, `Cancelled` + transizioni pure — _✅ `download/DownloadState.kt` (senza i casi tar di anti-vocale) + helper puri `isTerminal`/`isActive`; 3 test._
- [x] 3.2 Parsing di `Content-Range` e calcolo dell'offset di ripresa; decisione **200-vs-206** (200 = range ignorato → riparte da zero; 206 = append) + unit test sui casi limite — _✅ `DownloadHttp.parseContentRange`/`decideResume` (`ResumeDecision` StartFresh/Resume); 8 test (totale `*`, header malformato, fallback su stima)._
- [x] 3.3 Costruzione header `Range: bytes=N-` e `Authorization: Bearer …` (presente solo quando il token serve) + unit test — _✅ `DownloadHttp.rangeHeader`/`authHeader` (Bearer solo per token non vuoto); 4 test._
- [x] 3.4 `DownloadRateTracker` (velocità) e `ProgressThrottler` (rate-limit degli update UI) puri + unit test deterministici — _✅ portati con clock iniettabile (`now: () -> Long`) per determinismo; 9 test con clock finto (finestra scorrevole, ETA, soppressione/reset)._
- [x] 3.5 Mapping degli errori HTTP a tipi: `401/403` → auth/licenza, `416` → range non valido (ripartenza pulita), timeout/IO → retriabile + unit test — _✅ `DownloadException` (Unauthorized/Forbidden/RangeNotSatisfiable/HttpError/NetworkError/Cancelled) + `classifyHttpError` + `isRetriable`/`isAuthRelated`; 7 test._

## 4. Motore di download (IO di rete, porting `ResumeDownloadHelper`)

- [x] 4.1 `ResumeDownloadHelper.downloadWithResume(config, onProgress, onStateChange)`: `Range` su `.tmp`, append, sidecar `.size` per il totale tra riavvii, rename `.tmp`→target a fine corsa — _✅ portato su `HttpURLConnection`, usa i tipi puri del gruppo 3 (`DownloadHttp.decideResume`, `classifyHttpError`, rate/throttler); IOException → `NetworkError`._
- [x] 4.2 Retry con backoff (3 tentativi) sopra il motore, con annullamento via flag cooperativo — _✅ `downloadWithRetry` (suspend, `delay` con backoff esponenziale) ritenta solo gli errori `isRetriable`; annullamento via `config.isCancelled`._
- [x] 4.3 HEAD "public-first": `200` → pubblico (legge `Content-Length`/`Accept-Ranges`); `401/403` → richiede token; esposto come decisione tipata a monte del download — _✅ `ResumeDownloadHelper.checkAccess` → `AccessResult.Public/NeedsAuth/Failure`._

## 5. Autenticazione HuggingFace (porting da anti-vocale)

- [x] 5.1 `HuggingFaceOAuthConfig`: endpoint authorize/token/userinfo, `CLIENT_ID` da `BuildConfig`, redirect `it.netseven.raglocale://oauth2callback`, scope `read-repos`, `serviceConfig` AppAuth, `isConfigured()` — _✅ `clientId` da `BuildConfig.HF_OAUTH_CLIENT_ID`; `isConfigured()` falso se vuoto._
- [x] 5.2 `HuggingFaceTokenManager` (interfaccia) + impl con persistenza **cifrata** di access/refresh/scadenza, `getEffectiveToken()` (refresh se scaduto), `username`, `logout()`, `isLoggedIn` — _✅ interfaccia (seam) con `isExpired`/`needsRefresh` puri di default + `EncryptedHuggingFaceTokenManager` (EncryptedSharedPreferences, recovery KeyStore, ripristino stato all'avvio). `getEffectiveToken()` con refresh estratto in `HuggingFaceTokenProvider` (separazione persistenza/rete per testabilità — deviazione consapevole dalla mescolanza MANUAL/OAUTH di anti-vocale)._
- [x] 5.3 Unit test JVM del token manager con un **fake** della persistenza: scadenza → refresh, refresh fallito, logout, riavvio loggato — _✅ `FakeHuggingFaceTokenManager` + `HuggingFaceTokenProviderTest` (5 test, clock iniettabile: valido→no refresh, scaduto→refresh+persist, refresh fallito→null, logout→null, scadenza senza refresh token)._
- [x] 5.4 `HuggingFaceAuthManager` (unico a conoscere AppAuth): `startAuthFlow(activity, launcher)`, `handleAuthResult(...)` (scambio codice→token con PKCE S256, `prompt=consent`), `refreshAccessToken(...)` — _✅ implementa `TokenRefresher`; salva i token prima di recuperare lo username; `refresh()` via grant `refresh_token`._
- [x] 5.5 `HuggingFaceApiClient` (OkHttp): `validateToken(token) → username` via userinfo — _✅ endpoint userinfo OAuth; parser `parseUsername` puro (preferred_username/name/sub) con 4 test._
- [x] 5.6 Wiring in DI (`RagModule`) e degrado con grazia quando `isConfigured()` è falso — _✅ `di/HuggingFaceModule` (binds TokenManager→Encrypted, TokenRefresher→AuthManager); login disattivato ma pubblici scaricabili se non configurato._

## 6. Orchestrazione (estensione di `ModelRepository`)

- [x] 6.1 `download(model, target, onState)`: `StorageChecker` (blocco prima di iniziare con byte richiesti/disponibili) → HEAD public-first → token se serve → scarica nel `.part`/`.tmp` — _✅ `ModelRepository.download(model, getToken, onState, isCancelled)` via seam `ModelDownloader` (real delega a `ResumeDownloadHelper`); blocco su spazio insufficiente con byte mancanti._
- [x] 6.2 A fine download: verifica md5 esatto con `ImportVerifier` (riuso) e **move atomico** riusando lo staging dell'import; nessuno stato parziale nell'indice/attivo finché non è pronto e verificato — _✅ scarica nel `.part` (no rename nel motore), verifica md5, poi `moveIntoPlace`; su corruzione pulisce il `.part`._
- [x] 6.3 Embedder a due file (modello + tokenizer): pronto solo quando **entrambi** scaricati e verificati — _✅ itera `targets()`, salta i file già presenti e verificati; lo stato resta NOT_DOWNLOADED finché entrambi non sono pronti (macchina a stati per tipo)._
- [x] 6.4 Test Robolectric su `ModelRepository`: download fittizio a buon fine con verifica, md5 errato scartato senza lasciare l'attivo, ripresa dal `.tmp`, gated senza token → errore di auth senza parziali nell'indice — _✅ `ModelRepositoryDownloadTest` (5 test) + `FakeModelDownloader`; include il caso gated con token → header `Bearer`._

## 7. UI a card per modello (porting `ModelVariantCard`)

- [x] 7.1 Derivazione pura dello **stato-card** dal modello (assente / in download / parziale / pronto / attivo) + unit test — _✅ `ModelCardState.stato` + enum `CardStato`; 5 test sulle precedenze._
- [x] 7.2 `ModelManagerViewModel`: download/riprendi/annulla/cancella-parziale nello scope coroutine, esposizione di `DownloadState` e progresso; ripresa automatica del `.tmp` alla riapertura — _✅ stato download per modello (`combine`), annullamento cooperativo via set di id, `hasPartial` rilevato alla `refresh()` (la ripresa continua dal `.tmp`, attivata da "Riprendi"); espone `hfState`/`loginConfigured`._
- [x] 7.3 `ModelManagerScreen`: card con icona di stato, badge ATTIVO, descrizione/dimensione, progresso ricco (%, byte, velocità, ETA), sezione parziale con Riprendi/Annulla; bottoni per stato (assente→Scarica, download→Annulla, parziale→Riprendi/Cancella, pronto→Usa/Elimina) — _✅ riscritta attorno a `CardStato`; pill di stato testuali (lint-safe), `DownloadProgress`/`IndeterminateProgress`._
- [x] 7.4 Chip HuggingFace in cima: "Accesso: <username> / Esci" oppure "Accedi a HuggingFace"; badge "richiede login HF" sull'embedder se non loggato; "login non configurato" se `isConfigured()` è falso — _✅ `HuggingFaceChip` con launcher OAuth (`StartActivityForResult` → `onLoginResult`); badge "richiede l'accesso a HuggingFace" sui gated assenti._
- [x] 7.5 Riusare la stessa barra di progresso ricca anche per l'**import** esistente — _✅ `importFromUri` copia in loop con `onProgress`; il ViewModel lo mappa a `DownloadState.Downloading` → stessa barra._
- [x] 7.6 Lint pulito (`ktlintCheck` + `lintDebug`, nessun BOM/carattere invisibile) e `assembleDebug` verde — _✅ `test`, `ktlintCheck`, `lintDebug`, `assembleDebug` tutti verdi (un crash transitorio della cache di lint risolto con cache fresca)._

## 8. Validazione on-device (Poco, `am instrument`)

> ⏳ Da eseguire sul device. Prerequisiti: APK installato sul Poco e — per 8.2 — l'OAuth app HF
> registrata con `hfOauthClientId` in `local.properties` (vedi `docs/oauth-huggingface-setup.md`).

- [ ] 8.1 Download anonimo dell'LLM end-to-end con verifica md5 e progresso reale
- [ ] 8.2 Login HF + download dell'embedder gated (incluso il caso "accetta licenza una volta" comunicato in chiaro)
- [ ] 8.3 Ripresa dopo uscita dall'app (dal `.tmp`) e annullamento
- [ ] 8.4 Attivazione (`Usa`) del modello scaricato e prova in chat (parafrasi end-to-end)

## 9. Documentazione e chiusura

- [x] 9.1 README/`docs`: istruzioni per registrare l'OAuth app HuggingFace e impostare `hfOauthClientId` in `local.properties` — _✅ `docs/oauth-huggingface-setup.md` + pointer in `AGENTS.md`; chiave già documentata in `.env.example`._
- [ ] 9.2 Aggiornare lo stato del design doc (`2026-06-13-download-modelli-in-app-design.md`) da "proposto" a "implementato" con gli esiti della validazione on-device
