# Setup OAuth HuggingFace (download dei modelli gated)

Il download in-app dei modelli usa la strategia **public-first**: l'LLM (pubblico) si scarica
senza login; l'**embedder EmbeddingGemma** è *gated* su HuggingFace e richiede l'accesso
dell'utente. Il login avviene in-app via OAuth (AppAuth + Custom Tabs). Per abilitarlo serve
registrare **una volta** un'OAuth app HuggingFace e mettere il suo Client ID in `local.properties`.

> Senza questa configurazione l'app funziona lo stesso: il login HF è disattivato (la UI mostra
> "login non configurato") e i **modelli pubblici restano scaricabili**. È un degrado con grazia.

## Passi

1. Vai su <https://huggingface.co/settings/oauth/apps> → **New OAuth Application**.
2. Compila:
   - **Redirect URI**: `it.netseven.raglocale://oauth2callback`
   - **Scopes**: `read-repos`
3. Copia il **Client ID** generato.
4. In `local.properties` (file **non** committato) aggiungi:
   ```properties
   hfOauthClientId=IL_TUO_CLIENT_ID
   ```
5. Ricostruisci l'app. Il Client ID viene esposto come `BuildConfig.HF_OAUTH_CLIENT_ID` e lo
   schema di redirect è registrato via `manifestPlaceholders["appAuthRedirectScheme"]`.

## Note

- **Licenza del modello (gated:auto):** EmbeddingGemma richiede di accettare la licenza **una
  volta** sul web con il proprio account HF. Dopo l'accettazione il download in-app funziona col
  token dell'utente loggato. Il flusso di login lo comunica in chiaro.
- **Nessun segreto nel repo:** il Client ID sta solo in `local.properties` (vedi `.gitignore` e
  `.env.example`); i token dell'utente sono personali e vengono persistiti **cifrati** sul device.
- Riferimenti: design `docs/superpowers/specs/2026-06-13-download-modelli-in-app-design.md`,
  change OpenSpec `openspec/changes/download-modelli-in-app/` (decisione D5).
