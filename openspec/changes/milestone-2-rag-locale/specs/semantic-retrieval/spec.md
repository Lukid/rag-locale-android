# Delta: semantic-retrieval

## ADDED Requirements

### Requirement: Ricerca semantica top-K con score
Data una query in linguaggio naturale, il sistema SHALL calcolarne l'embedding con lo stesso embedder usato per l'indice e SHALL restituire i top-K chunk per similarità coseno, ordinati per score decrescente, ciascuno con il proprio score.

#### Scenario: Query con risultati ordinati
- **WHEN** l'utente pone una domanda su un documento indicizzato
- **THEN** il sistema restituisce i top-K chunk ordinati per similarità coseno, ognuno col suo score

#### Scenario: Test della parafrasi
- **WHEN** la query usa parole che non compaiono nel documento ma esprime un concetto presente in un chunk
- **THEN** il chunk pertinente compare tra i risultati con score competitivo (la ricerca è semantica, non per parola chiave)

### Requirement: Interfaccia tool-shaped
La ricerca SHALL essere esposta da un'interfaccia pura (query e topK in ingresso; lista di chunk con testo, score e riferimento al documento in uscita), indipendente da UI e generazione. Tutti i consumatori (pannello didattico, costruzione del prompt) SHALL passare da questa interfaccia.

#### Scenario: Consumatori disaccoppiati
- **WHEN** la UI didattica e il costruttore del prompt richiedono i chunk per la stessa domanda
- **THEN** entrambi ottengono i risultati dalla stessa interfaccia di ricerca, senza dipendere dall'implementazione dello store

### Requirement: Coerenza embedder tra indice e query
Il sistema SHALL garantire che query e indice usino lo stesso embedder. Se l'embedder attivo cambia rispetto a quello con cui l'indice è stato costruito, il sistema SHALL rilevarlo e richiedere la re-indicizzazione invece di restituire risultati incoerenti.

#### Scenario: Cambio di embedder con indice esistente
- **WHEN** l'utente attiva un embedder diverso da quello usato per costruire l'indice
- **THEN** il sistema segnala che l'indice non è compatibile e propone la re-indicizzazione del documento

### Requirement: topK configurabile
Il numero di chunk recuperati (topK) SHALL essere configurabile dall'utente entro limiti ragionevoli per il device.

#### Scenario: Modifica del topK
- **WHEN** l'utente cambia il valore di topK nelle impostazioni
- **THEN** le ricerche successive restituiscono il nuovo numero di chunk
