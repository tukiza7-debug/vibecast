package io.github.tukiza7_debug.vibecast.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pure parsing of a GitHub "latest release" payload into the APK this device should
 * install. Kept free of framework dependencies so it is unit testable on the JVM.
 */
object ReleaseParser {

    data class ParsedRelease(
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val checksumsUrl: String?,
    )

    fun parse(json: String, deviceAbi: String, appPrefix: String = "VibeCast"): ParsedRelease? {
        val element = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        val tagName = element.string("tag_name") ?: return null
        if (tagName.startsWith("site-")) return null
        if (element.bool("draft") == true) return null
        val version = tagName.removePrefix("v")
        val assets = element.array("assets") ?: return null
        var apk: JsonObject? = null
        var checksumsUrl: String? = null
        for (asset in assets) {
            val obj = asset as? JsonObject ?: continue
            val name = obj.string("name") ?: continue
            if (name.equals("SHA256SUMS.txt", ignoreCase = true) || name.equals("SHA256SUMS", ignoreCase = true)) {
                checksumsUrl = obj.string("browser_download_url")
                continue
            }
            if (!name.endsWith(".apk")) continue
            if (!name.startsWith("$appPrefix-$version-")) continue
            val match = APK_NAME_REGEX.find(name) ?: continue
            val abi = match.groupValues[1]
            val current = apk
            if (current == null || abi == deviceAbi) {
                apk = obj
            }
        }
        val apkObj = apk ?: return null
        return ParsedRelease(
            versionName = version,
            notes = element.string("body").orEmpty(),
            apkUrl = apkObj.string("browser_download_url") ?: return null,
            apkName = apkObj.string("name") ?: return null,
            checksumsUrl = checksumsUrl,
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    private fun JsonObject.array(key: String): List<kotlinx.serialization.json.JsonElement>? =
        (this[key] as? JsonArray)?.toList()

    val APK_NAME_REGEX = Regex("-(universal|arm64-v8a|armeabi-v7a|x86_64|x86)\\.apk$")
}
