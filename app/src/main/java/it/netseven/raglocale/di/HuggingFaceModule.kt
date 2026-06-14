package it.netseven.raglocale.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.netseven.raglocale.huggingface.EncryptedHuggingFaceTokenManager
import it.netseven.raglocale.huggingface.HuggingFaceAuthManager
import it.netseven.raglocale.huggingface.HuggingFaceTokenManager
import it.netseven.raglocale.huggingface.TokenRefresher
import javax.inject.Singleton

/**
 * Binding del sottosistema di autenticazione HuggingFace (capability `huggingface-auth`).
 * Il token manager cifrato è il seam di persistenza; l'AuthManager (AppAuth) è il refresher.
 * Degrado con grazia: i binding esistono sempre, ma il login è attivo solo se
 * `HuggingFaceOAuthConfig.isConfigured()` (Client ID presente).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HuggingFaceModule {
    @Binds
    @Singleton
    abstract fun bindTokenManager(impl: EncryptedHuggingFaceTokenManager): HuggingFaceTokenManager

    @Binds
    @Singleton
    abstract fun bindTokenRefresher(impl: HuggingFaceAuthManager): TokenRefresher
}
