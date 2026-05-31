## ADDED Requirements

### Requirement: Risposta in streaming
Il sistema SHALL inviare la risposta del modello in streaming, mostrando i token man mano che vengono generati, invece di attendere il completamento dell'intera risposta.

#### Scenario: Token mostrati progressivamente
- **WHEN** l'utente invia un messaggio e il modello inizia a generare
- **THEN** l'interfaccia mostra il testo che scorre token-per-token fino al termine della risposta

### Requirement: Chat solo testo
La chat SHALL accettare e produrre esclusivamente testo in questo milestone; input multimodali (immagini, audio) NON SHALL essere supportati.

#### Scenario: Conversazione testuale
- **WHEN** l'utente scrive un messaggio di testo e invia
- **THEN** il sistema lo passa direttamente al modello e mostra la risposta testuale, senza alcun grounding o documento

### Requirement: Cronologia della sessione
Il sistema SHALL mantenere la cronologia dei messaggi all'interno della sessione di chat corrente, così che il modello disponga del contesto conversazionale.

#### Scenario: Contesto mantenuto tra i turni
- **WHEN** l'utente invia un secondo messaggio che fa riferimento al primo
- **THEN** il sistema include la cronologia della sessione nel contesto passato al modello

### Requirement: Limite sui token di output
Il sistema SHALL applicare un limite massimo configurabile sui token di output (decode), per tenere le risposte entro tempi guardabili dato il throughput di decode del device.

#### Scenario: Risposta troncata al limite
- **WHEN** la generazione raggiunge il numero massimo di token configurato
- **THEN** il sistema interrompe la generazione in modo pulito e presenta la risposta prodotta fino a quel punto

### Requirement: Interruzione della generazione
Il sistema SHALL consentire all'utente di interrompere una generazione in corso.

#### Scenario: Stop manuale
- **WHEN** l'utente interrompe la generazione mentre i token stanno scorrendo
- **THEN** il sistema ferma la generazione e mantiene il testo già prodotto nella cronologia
