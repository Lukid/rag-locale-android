# Delta: grounded-answers

## ADDED Requirements

### Requirement: Prompt grounded sui chunk recuperati
Il sistema SHALL costruire il prompt per il modello includendo i chunk recuperati, numerati in modo stabile, con l'istruzione esplicita di rispondere solo sulla base del contesto fornito e di citare i numeri dei chunk usati.

#### Scenario: Risposta basata sul contesto
- **WHEN** l'utente pone una domanda la cui risposta è contenuta nei chunk recuperati
- **THEN** il modello genera una risposta coerente col contenuto dei chunk

#### Scenario: Informazione assente dal contesto
- **WHEN** l'utente pone una domanda la cui risposta non è nei chunk recuperati
- **THEN** la risposta dichiara che l'informazione non è presente nel documento invece di inventarla

### Requirement: Citazioni dei chunk usati
Il sistema SHALL estrarre dalla risposta generata i riferimenti ai chunk citati (marcatori numerici) e SHALL evidenziarli all'utente. Se la risposta non contiene marcatori validi, il sistema SHALL comunque mostrare i chunk recuperati senza indicarne di citati.

#### Scenario: Citazioni estratte ed evidenziate
- **WHEN** la risposta generata contiene marcatori di citazione validi
- **THEN** la UI evidenzia quali chunk recuperati sono stati citati nella risposta

#### Scenario: Nessun marcatore valido nella risposta
- **WHEN** la risposta generata non contiene marcatori di citazione riconoscibili
- **THEN** la UI mostra comunque i chunk recuperati con i loro score, senza evidenziarne di citati

### Requirement: Trasparenza didattica del retrieval
Per ogni domanda, la UI SHALL mostrare i chunk recuperati con i rispettivi score di similarità e l'indicazione di quali hanno alimentato la risposta. La trasparenza NON SHALL dipendere dalla qualità della risposta generata.

#### Scenario: Pannello didattico per ogni risposta
- **WHEN** una risposta viene generata (o fallisce la generazione)
- **THEN** l'utente può vedere i chunk recuperati per quella domanda, ordinati con i loro score

### Requirement: Generazione grounded coerente con la chat
La generazione in modalità RAG SHALL riusare il motore d'inferenza esistente e conservarne i comportamenti: risposta in streaming, interruzione manuale che mantiene il testo prodotto, cap sui token di output.

#### Scenario: Streaming e stop in modalità RAG
- **WHEN** una risposta grounded è in generazione e l'utente preme stop
- **THEN** i token già mostrati in streaming restano visibili e la generazione si interrompe in modo pulito
