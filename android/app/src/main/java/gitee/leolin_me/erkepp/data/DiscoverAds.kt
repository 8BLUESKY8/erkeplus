package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class DiscoverAd(val id: String, val title: String, val imageUrl: String, val targetUrl: String)

data class DiscoverAds(
    val top: List<DiscoverAd> = emptyList(),
    val bottom: List<DiscoverAd> = emptyList(),
    val showPlaceholders: Boolean = true,
)

/** 广告与官方业务接口隔离，仅接受 HTTPS 素材和 HTTP(S) 落地页。 */
fun parseDiscoverAds(source: String): DiscoverAds = runCatching {
    val root = Json.parseToJsonElement(source).jsonObject
    fun slot(name: String): List<DiscoverAd> {
        val rows = runCatching { root[name]?.jsonArray }.getOrNull() ?: return emptyList()
        val ids = mutableSetOf<String>()
        return rows.mapNotNull { row ->
            runCatching {
                val obj = row.jsonObject
                fun text(key: String) = obj[key]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                if (obj["enabled"]?.jsonPrimitive?.booleanOrNull == false) return@runCatching null
                val id = text("id")
                val image = text("imageUrl").toHttpUrlOrNull() ?: return@runCatching null
                val target = text("targetUrl").toHttpUrlOrNull() ?: return@runCatching null
                if (id.isBlank() || !image.isHttps || !ids.add(id)) return@runCatching null
                DiscoverAd(id, text("title").ifBlank { "推广内容" }, image.toString(), target.toString())
            }.getOrNull()
        }
    }
    DiscoverAds(slot("top"), slot("bottom"), root["showPlaceholders"]?.jsonPrimitive?.booleanOrNull ?: true)
}.getOrDefault(DiscoverAds())
