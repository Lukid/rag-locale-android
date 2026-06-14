package it.netseven.raglocale.modelmanager.download

import java.io.File

/**
 * Fake di [ModelDownloader] per i test di orchestrazione (niente rete): registra le chiamate e
 * delega la "scrittura" del file a [onDownload], così il test controlla esito e contenuto.
 */
class FakeModelDownloader(
    var access: AccessResult = AccessResult.Public(contentLength = 0L, acceptsRanges = true),
    var onDownload: (DownloadConfig) -> Result<File> = { Result.success(it.tempFile) },
) : ModelDownloader {
    val accessCalls = mutableListOf<String>()
    val downloadConfigs = mutableListOf<DownloadConfig>()

    override suspend fun checkAccess(
        url: String,
        authHeader: String?,
    ): AccessResult {
        accessCalls.add(url)
        return access
    }

    override suspend fun download(
        config: DownloadConfig,
        onState: (DownloadState) -> Unit,
    ): Result<File> {
        downloadConfigs.add(config)
        return onDownload(config)
    }
}
