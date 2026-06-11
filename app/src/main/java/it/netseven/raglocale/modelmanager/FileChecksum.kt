package it.netseven.raglocale.modelmanager

import java.io.File
import java.security.MessageDigest

/** Calcolo del checksum md5 di un file in streaming (per la verifica d'integrità dell'import). */
object FileChecksum {
    private const val BUFFER_BYTES = 1 shl 16

    fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
