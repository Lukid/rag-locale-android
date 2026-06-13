# Download in-app dei modelli — design

> Stato: **proposto** (in attesa di revisione). Data: 2026-06-13.
> Estende il Milestone 2 (RAG locale) con l'acquisizione dei modelli **dentro l'app**.
> Riferimento di implementazione sullo stesso stack: **anti-vocale**
> (`RisorseArtificiali/anti-vocale`, MIT) — gran parte dei componenti si portano quasi 1:1.

## Contesto e obiettivo

Oggi i modelli (LLM + embedder) si acquisiscono solo via **import da file** (file picker / `adb push`).
Per l'LLM significa spostare a mano 2,6 GB e, durante la copia, la UI non dà alcun feedback:
nessuna barra, solo un toast che sparisce, e nessuna distinzione visiva tra "presente",
"pronto" e "attivo". Per una demo rivolta a **utenti finali** è inusabile.

Obiettivo: l'utente scarica i modelli dal Model manager con **un tap** (come AI Edge Gallery),
con progresso reale, ripresa dopo interruzione e stati leggibili; e sul modello scaricato può
**attivarlo** e provarlo in chat. Questo risolve anche la UX dell'import esistente.

Questa feature **supera consapevolmente la decisione D8** del design M2 ("download in-app rinviato").
È coerente con la security policy del progetto (`CLAUDE.md`), che già elenca *"il download dei
modelli dal Model manager"* tra la rete permessa. Il dato privato dell'utente continua a non
lasciare il device: l'unica rete aggiunta è il fetch dei modelli da HuggingFace.

## Scope

**Incluso**
- Download di LLM ed embedder da HuggingFace nel Model manager, con verifica d'integrità md5.
- Login HuggingFace in-app via OAuth (serve per l'embedder, che è gated).
- Ripresa del download interrotto e retry; progresso con velocità/ETA; annullamento.
- UI a card per modello: stato chiaro (assente / in download / parziale / pronto / attivo),
  azioni **Scarica / Riprendi / Annulla / Usa / Elimina**; stessa barra anche per l'import.

**Escluso (YAGNI, deciso in brainstorming)**
- **Niente foreground service**: il download è una coroutine con resume+retry (come anti-vocale).
  Se l'utente esce dall'app il download si ferma e **riprende dal `.tmp`** alla riapertura.
- **Niente benchmark in-app**: sul modello scaricato c'è *Usa* (attiva) e si prova in chat. Il
  benchmark formale resta il test strumentato `RagBenchmarkTest` (GPU vs CPU).
- Niente download in background, niente OCR, niente nuovi modelli oltre a quelli del catalogo.

## Fatti verificati (2026-06-13)

- **LLM** `litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm`:
  **non gated** → download anonimo `200 OK`, `Content-Length = 2 588 147 712`, md5
  `1b8446203a216cfd31f6a2a22f75e5e5`.
- **Embedder** `litert-community/embeddinggemma-300m`: **gated: auto** → anonimo `401 GatedRepo`,
  serve un token; ad auto-approvazione (basta un account HF che ha accettato la licenza una volta).
  File: `embeddinggemma-300M_seq512_mixed-precision.tflite` (md5 `edd86dab69e9333794ed983b4ab6d0d3`)
  + `sentencepiece.model` (md5 `b0cab25d6777ffdf26856aaf6316fbbc`).
- **HuggingFace OAuth**: authorize `https://huggingface.co/oauth/authorize`, token
  `https://huggingface.co/oauth/token`, userinfo `https://huggingface.co/oauth/userinfo`,
  **PKCE S256** supportato, grant `authorization_code` + `refresh_token`. Scope `read-repos`.
- HF CDN supporta le **range request** (`Accept-Ranges`/`Content-Range`) → ripresa affidabile.

## Strategia di autenticazione: "public-first" (come AI Edge Gallery / anti-vocale)

Un solo percorso di download decide l'auth a runtime:
1. **HEAD** sull'URL del file. `200` → pubblico, scarica **senza token** (è il caso dell'LLM).
2. Altrimenti (`401`/`403`) → serve il token. Se l'utente è loggato su HuggingFace, usa il
   suo access token come `Authorization: Bearer …`; se non lo è, la UI guida al **login HF**
   e indica che quel modello richiede l'accesso (è il caso dell'embedder).

Così l'LLM (il file pesante) si scarica a zero attriti, e il login serve solo per l'embedder.

## Architettura e componenti

Confine chiave invariato: **a valle del move atomico** nello storage interno, tutto il resto
(`ModelRepository.activeModelFile`/`activeEmbedderFiles`, retrieval, chat) non cambia. Il
download è una nuova **sorgente di acquisizione** che termina nello stesso staging dell'import.

### 1. Catalogo — estensione dati (`ModelInfo`/`CompanionArtifact`)
Aggiunta di `downloadUrl: String?` e `gated: Boolean` ai descrittori, e **pin degli md5
canonici** ora noti (LLM, embedder, tokenizer) più la `sizeBytes` reale dell'LLM
(`2 588 147 712`, oggi è una stima a 3,1 GB). Nessuna logica nuova qui: solo dati che
abilitano URL di download e verifica esatta.

### 2. Autenticazione HuggingFace (porting da anti-vocale)
- `HuggingFaceOAuthConfig` — endpoint, `CLIENT_ID` (da `BuildConfig`, vedi Setup), `REDIRECT_URI`
  (`it.netseven.raglocale://oauth2callback`), scope `read-repos`, `serviceConfig` di AppAuth,
  `isConfigured()`.
- `HuggingFaceAuthManager` — flusso OAuth con **AppAuth** + Custom Tabs: `startAuthFlow(activity,
  launcher)`, `handleAuthResult(...)` (scambio codice→token), `refreshAccessToken(...)`,
  fetch userinfo per lo username. `prompt=consent` per ottenere il refresh token.
- `HuggingFaceTokenManager` (interfaccia) + impl — persiste access/refresh token e scadenza in
  modo **cifrato**, espone `getEffectiveToken()` (rinfresca se scaduto), `username`, `logout()`,
  `isLoggedIn`. Interfaccia = seam unit-testabile in JVM con un fake.
- `HuggingFaceApiClient` — `validateToken(token) → username` (OkHttp, già in progetto).

Responsabilità singola, dipendenza esterna confinata: solo `HuggingFaceAuthManager` conosce AppAuth.

### 3. Motore di download (porting `ResumeDownloadHelper` + retry da anti-vocale)
- `ResumeDownloadHelper.downloadWithResume(config, onProgress, onStateChange)` — `Range:
  bytes=N-`; `200` (range ignorato) → riparte da zero; `206` → parsing `Content-Range` per
  offset e totale; `401/403/416` → errori tipati; sidecar `.size` per conoscere il totale tra
  riavvii; append sul `.tmp`; rename `.tmp`→target a fine corsa. `401`/`403` mappati a errori di
  auth/licenza con messaggio chiaro.
- Helper retry con backoff (3 tentativi) + `DownloadRateTracker`/`ProgressThrottler` per
  velocità/ETA senza martellare la UI. Annullamento via flag cooperativo.
- `DownloadState` (sealed): `Idle`, `CheckingAccess`, `Connecting`, `Downloading(byte, totale,
  %, rate, eta)`, `Retrying`, `PartiallyDownloaded`, `Complete`, `Error`, `Cancelled`.
- Esecuzione: **coroutine** nello scope del ViewModel (no foreground service). Le parti pure
  (calcolo offset, parsing `Content-Range`, decisione 200-vs-206, mapping stati) sono
  unit-testabili in JVM; l'IO di rete è coperto on-device.

### 4. Orchestrazione (estensione di `ModelRepository`)
Nuovo `download(model, target, onState)` che: controlla lo spazio (`StorageChecker` esistente),
fa l'HEAD public-first, ottiene il token se serve, scarica nel `.part`/`.tmp`, **verifica con
`ImportVerifier`** (md5 esatto — lezione M1), e fa il **move atomico** riusando la pipeline di
staging già scritta per l'import. L'embedder scarica due file (modello + tokenizer). Niente
stato parziale nell'indice/attivo finché entrambi non sono pronti e verificati.

### 5. UI (porting del pattern `ModelVariantCard`)
- `ModelManagerScreen`/`ModelManagerViewModel` rifatti attorno a una **card per modello**:
  icona di stato (✓ pronto / ☁ in download / ▢ assente), **badge ATTIVO**, descrizione,
  dimensione. Progresso ricco (%, byte, velocità, ETA) durante download e import. Sezione
  **parziale** con *Riprendi/Annulla*. Bottoni per stato:
  - assente → **Scarica** (badge "richiede login HF" sull'embedder se non loggato)
  - in download → **Annulla**
  - parziale → **Riprendi / Cancella parziale**
  - pronto → **Usa** (se non già attivo) e **Elimina**
- Chip HuggingFace in cima: *"Accesso: \<username\> / Esci"* oppure *"Accedi a HuggingFace"*.
- L'attivazione (`Usa`) resta la `setActive` esistente: distinta e visibile rispetto a "pronto".

## Setup manuale (una volta) — OAuth app HuggingFace

Come anti-vocale, serve registrare una OAuth app HF; senza, il login non gira (l'LLM si scarica
comunque, è pubblico). Passi:
1. `https://huggingface.co/settings/oauth/apps` → **New OAuth Application**.
2. Redirect URI: `it.netseven.raglocale://oauth2callback`; scope `read-repos`.
3. Copiare il **Client ID** in `local.properties` (non committato): `hfOauthClientId=...`.
4. `build.gradle.kts`: leggere la chiave in `BuildConfig.HF_OAUTH_CLIENT_ID` e impostare
   `manifestPlaceholders["appAuthRedirectScheme"] = "it.netseven.raglocale"`.
5. Dipendenza `net.openid:appauth:0.11.1`; permesso `INTERNET` (già presente).

`HuggingFaceOAuthConfig.isConfigured()` degrada con grazia: se il Client ID manca, la UI mostra
"login non configurato" e lascia comunque scaricare i modelli pubblici.

## Gestione degli errori
- Spazio insufficiente → bloccato prima di iniziare, con byte richiesti/disponibili.
- `401`/`403` → messaggio "richiede accesso HuggingFace / licenza": invito al login, niente file
  parziali lasciati nell'indice.
- Rete giù / timeout → retry con backoff; dopo i tentativi, errore chiaro e `.tmp` conservato
  per la ripresa.
- md5 non corrispondente a fine download → file scartato (come l'import), messaggio "corrotto".
- `416` (range non valido) → `.tmp` eliminato e ripartenza pulita.

## Test
- **Unit JVM**: parsing `Content-Range` e calcolo offset di ripresa; decisione 200-vs-206;
  costruzione header `Range`/`Authorization`; macchina stati `DownloadState`; verifica md5
  (riuso `ImportVerifierTest`); `HuggingFaceTokenManager` con fake (scadenza/refresh/logout);
  derivazione dello stato-card dal modello.
- **On-device (Poco, `am instrument` — mai `connectedAndroidTest`: wipe!)**: download anonimo
  dell'LLM end-to-end con verifica md5; login HF + download embedder gated; ripresa dopo
  uscita dall'app; annullamento; attivazione e prova in chat.

## Rischi / trade-off
- **Dipendenza di setup esterna (OAuth app HF)** → confinata; degrado con grazia se assente;
  istruzioni nella spec e nel README.
- **Download senza foreground service** → muore se l'app va in background; mitigato da
  resume+retry (riprende dal `.tmp`). Accettato: è l'approccio di anti-vocale, adeguato a una
  demo che si scarica con l'app aperta. Se in validazione risultasse fastidioso, il foreground
  service è un'aggiunta isolata sopra lo stesso motore.
- **Token HF cifrato sul device** → nessun segreto nel repo (Client ID in `local.properties`);
  il token è personale dell'utente.
- **Gemma è gated:auto** → l'utente deve accettare la licenza una volta sul web; lo comunichiamo
  in chiaro nel flusso di login.

## Tracciamento implementazione
Diventa una **change OpenSpec dedicata** (proposal/design/specs/tasks), come è stato per il
Milestone 2. Questo documento è la spec di design di riferimento da cui derivare proposta e task.
