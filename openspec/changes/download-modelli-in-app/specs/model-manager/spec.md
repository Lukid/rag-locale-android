# Delta: model-manager

## MODIFIED Requirements

### Requirement: Download del modello con progresso e ripresa
Il sistema SHALL scaricare il modello selezionato da una sorgente remota (HuggingFace) mostrando il
progresso (percentuale, byte trasferiti, velocità ed ETA) e SHALL supportare la ripresa di un
download interrotto senza ricominciare da zero. L'acquisizione del file remoto SHALL essere una
**sorgente alternativa all'import** e SHALL terminare nello **stesso staging dell'import**: download
su file temporaneo, **verifica d'integrità tramite checksum md5 esatto** (non la sola dimensione) e
**spostamento atomico** finale. Il sistema SHALL decidere l'autenticazione a runtime con strategia
**"public-first"**: una richiesta preliminare verifica l'accesso al file e, se pubblico, scarica
**senza token**; se l'accesso è negato (richiede licenza/login), il sistema SHALL usare il token
HuggingFace dell'utente loggato (vedi capability `huggingface-auth`) e, se non loggato, SHALL
indicare che quel modello richiede l'accesso e guidare al login. Nessuno stato parziale SHALL
entrare nell'indice o diventare attivo finché tutti i file del modello non sono scaricati e
verificati; per un embedder composto da più file (modello + tokenizer) il modello è pronto solo
quando **tutti** i file verificano.

#### Scenario: Download pubblico completato (senza login)
- **WHEN** l'utente avvia il download di un modello il cui file è pubblico e la connessione resta disponibile
- **THEN** il sistema scarica senza token, mostra il progresso (percentuale, byte, velocità, ETA) fino al completamento, verifica il checksum md5, sposta atomicamente e marca il modello come pronto

#### Scenario: Download di un modello gated con login
- **WHEN** l'utente avvia il download di un modello il cui accesso è negato in forma anonima ed è loggato a HuggingFace
- **THEN** il sistema usa il token dell'utente per autorizzare il download e procede come per un download pubblico

#### Scenario: Download di un modello gated senza login
- **WHEN** l'utente avvia il download di un modello che richiede accesso ma non è loggato a HuggingFace
- **THEN** il sistema non avvia il download, indica che il modello richiede l'accesso a HuggingFace e invita al login, senza lasciare file parziali nell'indice

#### Scenario: Ripresa dopo interruzione
- **WHEN** un download viene interrotto (rete persa o app chiusa) e poi ripreso
- **THEN** il sistema riprende dal punto raggiunto sul file temporaneo invece di riscaricare l'intero file

#### Scenario: Retry su errore di rete transitorio
- **WHEN** durante il download si verifica un errore di rete transitorio
- **THEN** il sistema ritenta con backoff per un numero limitato di tentativi e, se tutti falliscono, mostra un errore chiaro conservando il file temporaneo per la ripresa

#### Scenario: Annullamento del download
- **WHEN** l'utente annulla un download in corso
- **THEN** il sistema interrompe il trasferimento e conserva (o scarta, su richiesta) il parziale senza marcare il modello come pronto

#### Scenario: File scaricato corrotto
- **WHEN** al termine del download il checksum md5 non corrisponde a quello atteso del modello
- **THEN** il sistema scarta il file, non marca il modello come pronto e mostra un errore di file corrotto

### Requirement: Stato e rimozione dei modelli
Il sistema SHALL mostrare lo stato di ciascun modello tra **assente** (non scaricato/importato),
**in download**, **parziale** (download interrotto con file temporaneo presente), **pronto** e
**attivo**, e SHALL distinguere visivamente "pronto" da "attivo". Per un modello in stato parziale
il sistema SHALL offrire le azioni **Riprendi** e **Cancella parziale**. Il sistema SHALL consentire
la rimozione di un modello pronto per liberare spazio.

#### Scenario: Modello in stato parziale con ripresa
- **WHEN** un download è stato interrotto e l'utente riapre il Model manager
- **THEN** il modello è mostrato come "parziale" con le azioni Riprendi e Cancella parziale

#### Scenario: Cancellazione di un parziale
- **WHEN** l'utente sceglie "Cancella parziale" su un modello in stato parziale
- **THEN** il sistema elimina il file temporaneo e riporta il modello allo stato "assente"

#### Scenario: Rimozione di un modello pronto
- **WHEN** l'utente rimuove un modello pronto
- **THEN** il sistema elimina il file dal dispositivo e aggiorna lo stato a "assente"
