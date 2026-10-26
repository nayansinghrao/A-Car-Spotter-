package com.example.util

import android.util.Base64
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer

/**
 * Custom Coil Fetcher that transparently decodes data URI and raw Base64 strings
 * into image byte streams for Coil's AsyncImage pipeline.
 */
class Base64DataFetcher(
    private val dataUri: String,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val pureBase64 = if (dataUri.contains("base64,")) {
            dataUri.substringAfter("base64,")
        } else if (dataUri.contains(",")) {
            dataUri.substringAfter(",")
        } else {
            dataUri
        }.trim().replace("\n", "").replace("\r", "")

        return try {
            val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
            if (bytes == null || bytes.isEmpty()) return null
            val buffer = Buffer().write(bytes)
            SourceResult(
                source = ImageSource(buffer, options.context),
                mimeType = "image/jpeg",
                dataSource = DataSource.MEMORY
            )
        } catch (e: Exception) {
            null
        }
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.startsWith("data:image/") ||
                data.startsWith("data:") ||
                (data.length > 100 && !data.startsWith("http") && !data.startsWith("file:") && !data.startsWith("content:") && !data.startsWith("/"))
            ) {
                return Base64DataFetcher(data, options)
            }
            return null
        }
    }
}
