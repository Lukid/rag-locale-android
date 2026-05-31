## ADDED Requirements

### Requirement: Catalogo dei modelli LLM
Il sistema SHALL presentare all'utente un elenco di modelli LLM scaricabili, ciascuno con metadati (nome, dimensione su disco, quantizzazione). Il modello **Gemma 4 E2B-it** (`litert-community/gemma-4-E2B-it-litert-lm`) SHALL essere presente come default suggerito. In questo milestone il catalogo SHALL contenere solo modelli LLM (nessun embedder).

#### Scenario: Visualizzazione del catalogo
- **WHEN** l'utente apre la schermata dei modelli
- **THEN** vede l'elenco dei modelli LLM disponibili con nome, dimensione e stato, con Gemma 4 E2B-it indicato come default

### Requirement: Download del modello con progresso e ripresa
Il sistema SHALL scaricare il modello selezionato mostrando il progresso, e SHALL supportare la ripresa di un download interrotto senza ricominciare da zero.

#### Scenario: Download completato
- **WHEN** l'utente avvia il download di un modello e la connessione resta disponibile
- **THEN** il sistema mostra il progresso fino al completamento e marca il modello come pronto

#### Scenario: Ripresa dopo interruzione
- **WHEN** un download viene interrotto (rete persa o app chiusa) e poi ripreso
- **THEN** il sistema riprende dal punto raggiunto invece di riscaricare l'intero file

### Requirement: Verifica dello spazio di storage
Il sistema SHALL verificare lo spazio libero prima di avviare un download e, se insufficiente per la dimensione del modello, SHALL impedire il download informando l'utente.

#### Scenario: Spazio insufficiente
- **WHEN** l'utente avvia un download e lo spazio libero è inferiore alla dimensione del modello
- **THEN** il sistema non avvia il download e mostra un messaggio chiaro sullo spazio mancante

### Requirement: Selezione del modello attivo
Il sistema SHALL consentire all'utente di scegliere quale modello scaricato è quello attivo per la chat.

#### Scenario: Cambio del modello attivo
- **WHEN** l'utente seleziona un modello scaricato diverso da quello attivo
- **THEN** il sistema imposta il nuovo modello come attivo per le sessioni di chat successive

### Requirement: Stato e rimozione dei modelli
Il sistema SHALL mostrare lo stato di ciascun modello (non scaricato / in download / pronto) e SHALL consentire la rimozione di un modello scaricato per liberare spazio.

#### Scenario: Rimozione di un modello
- **WHEN** l'utente rimuove un modello scaricato
- **THEN** il sistema elimina il file dal dispositivo e aggiorna lo stato a "non scaricato"
