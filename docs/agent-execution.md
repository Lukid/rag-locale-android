# Policy di esecuzione per agenti (sandbox)

Documento vendor-neutral su **come far girare in sicurezza i comandi** suggeriti o eseguiti
da un agente di coding su questo progetto. Pensato perché il progetto sia "agent-ready" dal day one.

## Principio

Concedi all'agente il minimo necessario. La maggior parte del lavoro (build di debug, unit test,
lint, analisi del codice) è sicura ed eseguibile senza isolamento speciale. Le azioni con effetti
esterni o difficilmente reversibili vanno isolate o confermate.

## Comandi sicuri da eseguire (senza conferma)

- `./gradlew test` — unit test JVM
- `./gradlew lint` / `./gradlew ktlintCheck` / `./gradlew detekt` — analisi statica
- `./gradlew assembleDebug` — build di debug
- `./gradlew clean`
- Comandi git di sola lettura: `git status`, `git diff`, `git log`, `git show`

## Richiedono conferma o sandbox

- Firma / build di **release**, generazione di artefatti firmati
- `git push`, creazione di tag, modifiche ai workflow di CI (`.github/workflows/`)
- Installazione di un APK su un **device** o emulatore (`adb install`, `connectedAndroidTest`)
- Qualsiasi comando che accede alla **rete** (download modelli, `UrlSource`, risoluzione dipendenze nuove)
- Modifiche a `local.properties`, keystore o file di credenziali

## Opzioni di sandbox (scegline una)

| Opzione | Quando | Note |
|---|---|---|
| **[LINCE](https://lince.sh)** | Sandbox locale leggera per agenti | Isola filesystem/rete mantenendo il flusso di lavoro |
| **Devcontainer** | Ambiente riproducibile condiviso col team | `.devcontainer/`; buon fit per CI parity |
| **OS-sandbox** (es. `sandbox-exec` su macOS, `bubblewrap`/`firejail` su Linux) | Isolamento a livello di processo | Limita accesso a fs/rete per singolo comando |
| **Hosted** (runner CI, ambiente effimero) | Esecuzioni non interattive / batch | Nessun accesso al device fisico; usa emulatori o test JVM |

> Nota Android: i test **strumentati** e l'inferenza reale dei modelli richiedono un device o
> emulatore e non girano in una sandbox JVM pura. Tienili fuori dal percorso automatico
> dell'agente: eseguili manualmente sul device target (il Poco X6 Pro 5G) come da spec.

## Segreti

I segreti non stanno mai nel repo (vedi `.gitignore` e `.env.example`). In sandbox/CI inietta
chiavi di firma e credenziali tramite i secret dell'ambiente, mai in chiaro nel codice o nei log.
