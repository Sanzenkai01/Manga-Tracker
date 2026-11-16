package com.sanzenkai.mangatracker.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.sanzenkai.mangatracker.core.domain.repository.MangaRepository
import java.io.IOException
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MangaBackupExporter {
    suspend fun export(destination: Uri)
}

class JsonMangaBackupExporter(
    private val mangaRepository: MangaRepository,
    private val contentResolver: ContentResolver,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : MangaBackupExporter {
    override suspend fun export(destination: Uri) = withContext(Dispatchers.IO) {
        val snapshot = mangaRepository.createBackupSnapshot(nowEpochMillis())
        val json = MangaBackupJsonCodec.encode(snapshot)
        val output = contentResolver.openOutputStream(destination, "w")
            ?: throw IOException("The selected backup destination could not be opened.")
        OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
            writer.write(json)
            writer.write("\n")
        }
    }
}
