package it.netseven.raglocale.modelmanager.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seam del download usato da `ModelRepository`: isola la rete (HEAD public-first + trasferimento
 * con ripresa) così l'orchestrazione è testabile in JVM/Robolectric con un fake. L'impl reale
 * delega a [ResumeDownloadHelper].
 */
interface ModelDownloader {
    suspend fun checkAccess(
        url: String,
        authHeader: String?,
    ): AccessResult

    suspend fun download(
        config: DownloadConfig,
        onState: (DownloadState) -> Unit,
    ): Result<File>
}

/** Impl reale: delega a [ResumeDownloadHelper] su un dispatcher IO. */
@Singleton
class RealModelDownloader
    @Inject
    constructor() : ModelDownloader {
        override suspend fun checkAccess(
            url: String,
            authHeader: String?,
        ): AccessResult = withContext(Dispatchers.IO) { ResumeDownloadHelper.checkAccess(url, authHeader) }

        override suspend fun download(
            config: DownloadConfig,
            onState: (DownloadState) -> Unit,
        ): Result<File> = withContext(Dispatchers.IO) { ResumeDownloadHelper.downloadWithRetry(config, onStateChange = onState) }
    }
