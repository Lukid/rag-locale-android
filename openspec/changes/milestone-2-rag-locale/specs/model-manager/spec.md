# Delta: model-manager

## ADDED Requirements

### Requirement: Catalogo dei modelli per tipo
Il sistema SHALL presentare un catalogo di modelli distinti per tipo: **LLM** e **embedder**, ciascuno con metadati (nome, dimensione su disco, quantizzazione). Il modello **Gemma 4 E2B-it** SHALL essere il default suggerito tra gli LLM; **EmbeddingGemma** SHALL essere il default suggerito tra gli embedder (con **Gecko** come alternativa di riserva).

#### Scenario: Visualizzazione del catalogo con i due tipi
- **WHEN** l'utente apre la schermata dei modelli
- **THEN** vede i modelli raggruppati o etichettati per tipo (LLM, embedder), con nome, dimensione e stato, e i default suggeriti indicati

### Requirement: Selezione dell'embedder attivo
Il sistema SHALL consentire all'utente di scegliere quale embedder disponibile è quello attivo per la pipeline RAG (indicizzazione e query), in modo analogo alla selezione del LLM attivo per la chat.

#### Scenario: Cambio dell'embedder attivo
- **WHEN** l'utente seleziona un embedder disponibile diverso da quello attivo
- **THEN** il sistema imposta il nuovo embedder come attivo per le operazioni RAG successive (la coerenza con l'indice esistente è gestita dalla capability semantic-retrieval)

### Requirement: Import di un modello da file con verifica d'integrità
Il sistema SHALL consentire l'acquisizione di un modello (LLM o embedder) tramite import di un file presente sul device. L'import SHALL avvenire su file temporaneo con spostamento atomico finale e SHALL verificare l'integrità del file importato (checksum, non la sola dimensione) prima di marcare il modello come pronto; in caso di mismatch il file SHALL essere scartato con un messaggio chiaro.

#### Scenario: Import riuscito con verifica
- **WHEN** l'utente importa un file modello valido e lo spazio è sufficiente
- **THEN** il sistema copia su file temporaneo, verifica il checksum, sposta atomicamente e marca il modello come pronto

#### Scenario: Import con file corrotto
- **WHEN** il file importato ha dimensione attesa ma checksum non corrispondente al contenuto sorgente
- **THEN** il sistema scarta il file, non marca il modello come pronto e mostra un errore chiaro

## REMOVED Requirements

### Requirement: Catalogo dei modelli LLM
**Reason**: sostituito dal catalogo per tipo (LLM + embedder): il vincolo "solo modelli LLM, nessun embedder" valeva per il Milestone 1 e decade con l'introduzione della pipeline RAG.
**Migration**: i comportamenti del catalogo LLM sono interamente coperti dal nuovo requirement "Catalogo dei modelli per tipo"; nessuna azione richiesta sui dati esistenti.
