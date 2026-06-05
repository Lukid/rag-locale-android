# Repo di riferimento

Queste repo sono riferimenti stabili per l'implementazione dell'app. Prima di toccare
inferenza, model manager, download/import modelli o configurazione LiteRT-LM, confronta
il codice locale con questi progetti.

## Anti-vocale

- URL: https://github.com/RisorseArtificiali/anti-vocale
- Clone locale corrente: `/private/tmp/anti-vocale` (shallow clone di lavoro).
- Ruolo: riferimento applicativo vicino al nostro stack Kotlin/Compose/Hilt + LiteRT-LM
  con Gemma 4 E2B.
- Da guardare: inizializzazione del motore, selezione backend, lifecycle singleton,
  keep-alive/unload, gestione errori nativi e pattern UI intorno allo streaming.

## Google AI Edge Gallery

- URL: https://github.com/google-ai-edge/gallery
- Clone locale corrente: `/private/tmp/ai-edge-gallery` (shallow clone di lavoro).
- Ruolo: riferimento ufficiale Google per AI Edge Gallery, model management e uso
  mobile di LiteRT-LM.
- Da guardare: import/download modelli, configurazione dei modelli multimodali,
  parametri di default, mapping backend per testo/vision/audio, prompt/chat template
  e diagnostica di performance.

Nota: i clone in `/private/tmp` sono comodi per grep/diff durante le sessioni agente,
ma possono essere ricreati dai link se il sistema li pulisce.
