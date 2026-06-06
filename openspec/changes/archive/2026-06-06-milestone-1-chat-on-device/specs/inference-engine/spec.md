## ADDED Requirements

### Requirement: Caricamento del modello in una sessione eseguibile
Il sistema SHALL caricare il modello attivo in una sessione di inferenza eseguibile basata su LiteRT-LM, pronta a generare testo.

#### Scenario: Caricamento riuscito
- **WHEN** l'utente avvia la chat con un modello pronto
- **THEN** il sistema carica il modello in una sessione di inferenza e segnala che è pronto a rispondere

#### Scenario: Indicatore durante il caricamento
- **WHEN** il caricamento del modello è in corso (può richiedere diversi secondi)
- **THEN** il sistema mostra un indicatore di caricamento e non accetta input finché la sessione non è pronta

### Requirement: Selezione del backend di inferenza
Il sistema SHALL consentire all'utente di scegliere il backend di esecuzione tra **GPU** e **CPU**, con **GPU** come default.

#### Scenario: Default su GPU
- **WHEN** l'utente non ha modificato la preferenza di backend
- **THEN** il sistema tenta di caricare il modello sul backend GPU

#### Scenario: Scelta esplicita del backend
- **WHEN** l'utente imposta il backend su CPU
- **THEN** le sessioni successive vengono caricate su CPU

### Requirement: Fallback GPU→CPU con avviso
Quando l'inizializzazione sul backend GPU fallisce, il sistema SHALL ripiegare automaticamente sul backend CPU e SHALL informare l'utente. L'avviso SHALL spiegare il compromesso in modo accurato (la CPU è più lenta sulla fase di prefill ma può essere più rapida su decode e caricamento), evitando di presentare la CPU come mero degrado.

#### Scenario: Init GPU fallita
- **WHEN** il caricamento del modello su GPU fallisce
- **THEN** il sistema carica il modello su CPU e mostra un avviso che descrive accuratamente il compromesso di prestazioni

### Requirement: Modello residente per la sessione
Il sistema SHALL mantenere il modello caricato in memoria per la durata della sessione e NON SHALL ricaricarlo a ogni query.

#### Scenario: Più query senza ricaricamento
- **WHEN** l'utente invia più messaggi consecutivi nella stessa sessione
- **THEN** il sistema riusa la sessione di inferenza già caricata senza ripetere l'inizializzazione del modello
