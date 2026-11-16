package com.sanzenkai.mangatracker.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MangaBackupImporter {
    suspend fun importBackup(source: Uri)
}

class JsonMangaBackupImporter(
    private val mangaRepository: MangaRepository,
    private val contentResolver: ContentResolver,
) : MangaBackupImporter {
    override suspend fun importBackup(source: Uri) = withContext(Dispatchers.IO) {
        val input = contentResolver.openInputStream(source)
            ?: throw IOException("The selected backup file could not be opened.")
        val json = input.use(::readBoundedUtf8)
        mangaRepository.restoreBackup(MangaBackupJsonCodec.decode(json))
    }

    private fun readBoundedUtf8(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (totalBytes + count > MAX_BACKUP_SIZE_BYTES) {
                throw IOException("The selected backup file is too large.")
            }
            output.write(buffer, 0, count)
            totalBytes += count
        }
        return output.toByteArray().toString(Charsets.UTF_8).removePrefix("\uFEFF")
    }

    private companion object {
        const val MAX_BACKUP_SIZE_BYTES = 20 * 1024 * 1024
    }
}
