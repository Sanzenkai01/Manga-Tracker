package com.sanzenkai.mangatracker.notifications

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.sanzenkai.mangatracker.core.data.network.CoverImageFetchResult
import com.sanzenkai.mangatracker.core.data.network.SafeCoverImageFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafeCoverBitmapLoader(
    private val imageFetcher: SafeCoverImageFetcher = SafeCoverImageFetcher(),
) : CoverBitmapLoader {
    override suspend fun load(url: String): Bitmap? =
        when (val result = imageFetcher.fetch(url)) {
            is CoverImageFetchResult.Failure -> {
                Log.w(TAG, "Cover image could not be loaded: ${result.reason}")
                null
            }
            is CoverImageFetchResult.Success -> withContext(Dispatchers.IO) {
                decode(result.bytes)
            }
        }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "Downloaded cover was not a decodable bitmap.")
            return null
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: run {
                Log.w(TAG, "Downloaded cover could not be decoded.")
                null
            }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > MAX_BITMAP_DIMENSION) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val TAG = "MangaCoverImage"
        const val MAX_BITMAP_DIMENSION = 1_024
    }
}
