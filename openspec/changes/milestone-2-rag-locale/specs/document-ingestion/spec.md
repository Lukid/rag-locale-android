# Delta: document-ingestion

## ADDED Requirements

### Requirement: Ingestion da file di testo
Il sistema SHALL acquisire un documento da file `.txt` o `.md` scelto dall'utente e produrre un `NormalizedText` (testo normalizzato, senza artefatti di formato).

#### Scenario: Import di un file di testo
- **WHEN** l'utente seleziona un file `.txt` o `.md` dal device
- **THEN** il sistema ne estrae il testo normalizzato e avvia la pipeline di indicizzazione

### Requirement: Ingestion da PDF
Il sistema SHALL estrarre il layer testuale di un PDF e produrre un `NormalizedText`. Se il PDF non ha layer testo (scansione), il sistema SHALL rilevarlo e informare l'utente che l'OCR non è supportato.

#### Scenario: PDF con layer testo
- **WHEN** l'utente seleziona un PDF che contiene testo estraibile
- **THEN** il sistema ne estrae il testo normalizzato e avvia la pipeline di indicizzazione

#### Scenario: PDF scansionato senza layer testo
- **WHEN** l'utente seleziona un PDF privo di layer testuale
- **THEN** il sistema lo rileva, non indicizza nulla e mostra un messaggio chiaro (OCR non supportato)

### Requirement: Ingestion da URL
Il sistema SHALL scaricare una pagina web indicata dall'utente (unica operazione di rete della pipeline, eseguita una volta su richiesta) ed estrarne il contenuto principale come `NormalizedText`. Se l'estrazione del contenuto principale fallisce, il sistema SHALL ripiegare sul testo grezzo della pagina; se la pagina non è raggiungibile o risponde con errore, il sistema SHALL mostrare un errore chiaro senza lasciare stato parziale nell'indice.

#### Scenario: Articolo estratto correttamente
- **WHEN** l'utente incolla l'URL di una pagina con contenuto articolato e la rete è disponibile
- **THEN** il sistema estrae il contenuto principale (senza boilerplate) e avvia la pipeline di indicizzazione

#### Scenario: Estrazione del contenuto principale fallita
- **WHEN** l'estrattore di contenuto principale non riesce a isolare l'articolo
- **THEN** il sistema ripiega sul testo grezzo della pagina e lo segnala all'utente

#### Scenario: URL irraggiungibile o in errore
- **WHEN** l'URL è offline, va in timeout o risponde 4xx/5xx
- **THEN** il sistema mostra un errore chiaro e l'indice resta invariato

### Requirement: Normalizzazione indipendente dalla sorgente
Ogni sorgente SHALL produrre lo stesso tipo `NormalizedText`; da quel punto la pipeline (chunking, embedding, indicizzazione) SHALL essere identica e ignara dell'origine del documento.

#### Scenario: Pipeline identica a valle delle sorgenti
- **WHEN** due documenti equivalenti vengono acquisiti da sorgenti diverse (es. testo e PDF)
- **THEN** la pipeline a valle applica le stesse operazioni e produce chunk equivalenti

### Requirement: Chunking configurabile
Il sistema SHALL spezzare il `NormalizedText` in chunk con dimensione e overlap configurabili, preferendo i confini di frase o paragrafo per i tagli. I casi limite (testo più corto di un chunk, overlap maggiore o uguale alla dimensione) SHALL essere gestiti senza errori.

#### Scenario: Testo spezzato con overlap
- **WHEN** un testo più lungo della dimensione di chunk viene processato
- **THEN** il sistema produce chunk consecutivi che si sovrappongono per l'overlap configurato, con tagli preferenziali ai confini di frase

#### Scenario: Testo più corto di un chunk
- **WHEN** il testo normalizzato è più corto della dimensione di chunk
- **THEN** il sistema produce un singolo chunk con l'intero testo

### Requirement: Indicizzazione persistente con progresso
Il sistema SHALL calcolare l'embedding di ogni chunk e salvare chunk ed embedding nel vector store persistente, mostrando il progresso dell'operazione. L'indice SHALL sopravvivere al riavvio dell'app.

#### Scenario: Documento indicizzato e persistente
- **WHEN** l'ingestion di un documento si conclude e l'app viene riavviata
- **THEN** l'indice del documento è ancora disponibile per la ricerca senza ripetere l'ingestion

#### Scenario: Progresso visibile durante l'ingestion
- **WHEN** l'indicizzazione di un documento è in corso
- **THEN** la UI mostra l'avanzamento (chunk processati sul totale)

### Requirement: Limiti del documento
Il sistema SHALL rifiutare un documento vuoto con un messaggio chiaro e SHALL applicare un limite massimo di chunk per documento, informando l'utente se il documento lo eccede.

#### Scenario: Documento vuoto
- **WHEN** la sorgente produce un testo vuoto o di soli spazi
- **THEN** il sistema non indicizza nulla e mostra un messaggio chiaro

#### Scenario: Documento troppo grande
- **WHEN** il documento produce più chunk del limite massimo configurato
- **THEN** il sistema interrompe (o tronca) l'ingestion e informa l'utente del limite
