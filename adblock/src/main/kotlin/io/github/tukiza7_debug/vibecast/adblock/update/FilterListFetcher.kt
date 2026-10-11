package io.github.tukiza7_debug.vibecast.adblock.update

import io.github.tukiza7_debug.vibecast.adblock.rules.FilterParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Downloads filter lists with ETag-based conditional requests, validates the file,
 * and swaps it in atomically. On any failure the previous (or bundled) file stays.
 */
class FilterListFetcher
    @Inject
    constructor(private val client: OkHttpClient) {
        sealed class Result {
            object Unmodified : Result()

            data class Updated(val etag: String?, val rulesParsed: Int) : Result()

            data class Failed(val reason: String) : Result()
        }

        /**
         * @param target file to write the new list into
         * @param currentEtag etag from the previous successful download, if any
         */
        fun updateIfChanged(
            url: String,
            target: File,
            currentEtag: String?,
        ): Result {
            val builder = Request.Builder().url(url).header("User-Agent", "VibeCast-AdBlock/1.0")
            if (currentEtag != null) builder.header("If-None-Match", currentEtag)
            val response =
                try {
                    client.newCall(builder.build()).execute()
                } catch (e: IOException) {
                    return Result.Failed("network error: ${e.message}")
                }
            response.use { resp ->
                when {
                    resp.code == 304 -> return Result.Unmodified
                    !resp.isSuccessful -> return Result.Failed("HTTP ${resp.code} for $url")
                }
                val bodyText =
                    try {
                        resp.body?.string() ?: return Result.Failed("empty body")
                    } catch (e: IOException) {
                        return Result.Failed("read error: ${e.message}")
                    }
                val etag = resp.header("ETag")
                if (!isValidList(bodyText)) return Result.Failed("downloaded content is not a filter list")
                val rules = FilterParser.parse(bodyText).network.size
                val tmp = File(target.parentFile, target.name + ".tmp")
                try {
                    tmp.writeText(bodyText)
                    if (!tmp.renameTo(target)) {
                        target.writeText(bodyText)
                        tmp.delete()
                    }
                } catch (e: IOException) {
                    tmp.delete()
                    return Result.Failed("write error: ${e.message}")
                }
                return Result.Updated(etag, rules)
            }
        }

        private fun isValidList(text: String): Boolean {
            if (text.length < 200) return false
            val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
            return first.startsWith("[Adblock") || first.startsWith("!")
        }
    }
