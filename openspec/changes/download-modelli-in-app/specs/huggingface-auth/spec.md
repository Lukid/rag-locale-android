# Delta: huggingface-auth

## ADDED Requirements

### Requirement: Login a HuggingFace via OAuth in-app
Il sistema SHALL consentire all'utente di autenticarsi a HuggingFace **dentro l'app** tramite un
flusso OAuth `authorization_code` con **PKCE S256**, aprendo l'autorizzazione in una Custom Tab e
ricevendo il risultato sul redirect URI registrato. Al successo il sistema SHALL ottenere un access
token e un refresh token e SHALL ricavare e mostrare lo **username** dell'utente. Il login SHALL
essere richiesto solo per scaricare modelli ad accesso ristretto (gated); i modelli pubblici SHALL
restare scaricabili senza login.

#### Scenario: Login riuscito
- **WHEN** l'utente avvia il login a HuggingFace e completa l'autorizzazione nella Custom Tab
- **THEN** il sistema scambia il codice con access e refresh token, recupera lo username e mostra lo stato "Accesso: <username>"

#### Scenario: Login annullato dall'utente
- **WHEN** l'utente chiude la Custom Tab senza completare l'autorizzazione
- **THEN** il sistema resta nello stato non loggato e non altera i token eventualmente già presenti

### Requirement: Persistenza cifrata e refresh automatico del token
Il sistema SHALL persistere access token, refresh token e relativa scadenza in forma **cifrata**
sul device. SHALL esporre un token effettivo che, se scaduto, viene **rinfrescato automaticamente**
tramite il refresh token prima dell'uso. I token sono personali dell'utente e nessun segreto
relativo all'app (es. Client ID) SHALL essere committato nel repository.

#### Scenario: Refresh trasparente di un token scaduto
- **WHEN** un download gated richiede il token e l'access token risulta scaduto
- **THEN** il sistema usa il refresh token per ottenere un nuovo access token e procede senza richiedere un nuovo login

#### Scenario: Persistenza tra riavvii
- **WHEN** l'utente, già loggato, riapre l'app
- **THEN** il sistema legge i token cifrati e mostra lo stato loggato con lo username, senza ripetere il login

### Requirement: Logout
Il sistema SHALL consentire all'utente di disconnettersi, eliminando i token persistiti e
riportando lo stato a non loggato.

#### Scenario: Logout
- **WHEN** l'utente sceglie "Esci" dal chip HuggingFace
- **THEN** il sistema elimina i token cifrati e mostra nuovamente l'azione "Accedi a HuggingFace"

### Requirement: Degrado con grazia se l'OAuth non è configurato
Il sistema SHALL funzionare anche quando l'OAuth app HuggingFace non è configurata (Client ID
assente): in tal caso SHALL indicare che il login non è configurato e SHALL comunque consentire il
download dei modelli pubblici.

#### Scenario: Client ID assente
- **WHEN** l'app è avviata senza Client ID OAuth configurato
- **THEN** il sistema mostra "login non configurato", nasconde o disabilita il login HuggingFace e lascia scaricare i modelli pubblici
