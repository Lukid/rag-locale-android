## Why

Oggi i modelli (LLM da 2,6 GB + embedder) si acquisiscono solo via **import da file**
(file picker / `adb push`): nessuna barra di progresso, solo un toast che sparisce, e nessuna
distinzione visiva tra "presente", "pronto" e "attivo". Per una demo rivolta a **utenti finali**
è inusabile. Questa change rende reale l'acquisizione **dentro l'app**, con un tap, progresso,
ripresa e stati leggibili — superando consapevolmente la decisione D8 del Milestone 2
("download in-app rinviato"). Riferimento: design approvato
`docs/superpowers/specs/2026-06-13-download-modelli-in-app-design.md`.

## What Changes

- **Download in-app** di LLM ed embedder da HuggingFace nel Model manager, con verifica
  d'integrità **md5 esatto** (lezione M1: la sola dimensione non basta) e **move atomico** che
  riusa lo staging già scritto per l'import.
- **Strategia "public-first"**: HEAD sull'URL → `200` scarica senza token (LLM, non gated);
  `401`/`403` → serve il token HuggingFace (embedder, gated:auto).
- **Login HuggingFace in-app via OAuth** (AppAuth + Custom Tabs, PKCE S256), con token
  access/refresh persistiti **cifrati** sul device; chip con username/Esci.
- **Ripresa e retry** del download via **coroutine** nello scope del ViewModel (no foreground
  service): range request `bytes=N-`, append sul `.tmp`, backoff su 3 tentativi, annullamento.
- **UI a card per modello**: stato chiaro (assente / in download / parziale / pronto / attivo),
  azioni **Scarica / Riprendi / Annulla / Usa / Elimina**; stessa barra ricca (%, byte, velocità,
  ETA) anche per l'import esistente.
- **Catalogo arricchito**: `downloadUrl`, `gated` e md5 canonici pinnati (LLM, embedder,
  tokenizer) + `sizeBytes` reale dell'LLM (`2 588 147 712`, oggi stima a 3,1 GB).

Non-goals (YAGNI, decisi in brainstorming): niente foreground service, niente download in
background, niente benchmark in-app, niente nuovi modelli oltre al catalogo.

## Capabilities

### New Capabilities
- `huggingface-auth`: login OAuth a HuggingFace in-app, gestione cifrata di access/refresh token
  con refresh automatico e logout, esposizione dello username; usata dal download per i repo gated.

### Modified Capabilities
- `model-manager`: il requirement "Download del modello con progresso e ripresa" (finora
  documentato ma **non implementato**, D8 rinviato) diventa comportamento reale con auth
  public-first, verifica md5 e ripresa dal `.tmp`; il catalogo acquisisce i metadati di download
  (`downloadUrl`, `gated`, md5, size reale); lo stato dei modelli si estende a **parziale** con
  ripresa/cancellazione; l'acquisizione include una nuova sorgente (download) accanto all'import.

## Impact

- **Codice app**: `ModelInfo`/`CompanionArtifact` (metadati), `ModelRepository` (orchestrazione
  download), nuovo motore `ResumeDownloadHelper` + retry/throttler/rate-tracker, `DownloadState`
  sealed; nuovi `HuggingFaceOAuthConfig`/`AuthManager`/`TokenManager`/`ApiClient`;
  `ModelManagerScreen`/`ModelManagerViewModel` rifatti attorno alle card. Riuso di
  `StorageChecker` e `ImportVerifier` esistenti.
- **Dipendenze**: aggiunta `net.openid:appauth:0.11.1`; OkHttp già presente; permesso `INTERNET`
  già presente.
- **Setup esterno (una volta)**: registrazione OAuth app HuggingFace; `hfOauthClientId` in
  `local.properties` (non committato) esposto come `BuildConfig.HF_OAUTH_CLIENT_ID`;
  `manifestPlaceholders["appAuthRedirectScheme"]`. Degrado con grazia se assente: i modelli
  pubblici si scaricano comunque.
- **Confine invariato**: a valle del move atomico nello storage interno, retrieval e chat non
  cambiano. L'unica rete aggiunta è il fetch dei modelli da HuggingFace; il dato privato resta
  on-device.
