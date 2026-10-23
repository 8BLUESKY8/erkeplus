package gitee.leolin_me.erkepp.data

import gitee.leolin_me.erkepp.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 检查更新的结果。 */
sealed interface UpdateResult {
    /** 有新版本。[url] 是发布页地址，[apkUrl] 是发布里附带的安装包（可能没有）。 */
    data class Available(
        val version: Version,
        val url: String,
        val notes: String,
        val apkUrl: String? = null,
    ) : UpdateResult

    /** 已经是最新。 */
    object UpToDate : UpdateResult

    /** 仓库里还没有任何发布版本或标签。 */
    object NoRelease : UpdateResult

    /** 网络或解析失败。 */
    data class Failed(val message: String) : UpdateResult
}

/**
 * 从 Gitee 仓库读取最新版本。
 *
 * 先看 Release，没有 Release 就退回到 Tag；两者都是公开读取，不需要令牌。
 * 仓库地址与当前版本都由调用方传入，方便以后换仓库。
 */
class UpdateChecker(
    private val owner: String = "LeoLin_ME",
    private val repo: String = "erkeplus",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(current: String): UpdateResult = withContext(Dispatchers.IO) {
        val currentVersion = Version.parse(current)
            ?: return@withContext UpdateResult.Failed("当前版本号无法解析")

        runCatching { fetchLatest() }
            .fold(
                onSuccess = { latest ->
                    AppLog.d("update", "latest=${latest?.version} current=$current")
                    if (latest == null) {
                        UpdateResult.NoRelease
                    } else if (latest.version > currentVersion) {
                        UpdateResult.Available(latest.version, latest.url, latest.notes, latest.apkUrl)
                    } else {
                        UpdateResult.UpToDate
                    }
                },
                onFailure = { UpdateResult.Failed(it.message ?: "网络不可用") },
            )
    }

    internal data class Latest(
        val version: Version,
        val url: String,
        val notes: String,
        val apkUrl: String?,
    )

    private fun fetchLatest(): Latest? {
        // per_page 给大一点：Gitee 的发行版列表是「旧的在前面」，只取前几条会漏掉最新版本
        val releases = getArray("https://gitee.com/api/v5/repos/$owner/$repo/releases?per_page=100", allowMissing = true)
        pickLatestRelease(releases)?.let { return it }

        val tags = getArray("https://gitee.com/api/v5/repos/$owner/$repo/tags?per_page=100", allowMissing = true)
        return pickLatestTag(tags)
    }

    /**
     * 在发行版列表里取**版本号最大**的那个。
     *
     * 不能取第一个：Gitee 按创建时间正序返回，第一个往往是最老的版本，
     * 取第一个会让「检查更新」永远显示已是最新（0.8.8 就踩过这个坑）。
     */
    internal fun pickLatestRelease(releases: JsonArray): Latest? {
        var best: Latest? = null
        for (item in releases) {
            val release = runCatching { item.jsonObject }.getOrNull() ?: continue
            val tag = release.string("tag_name") ?: continue
            val version = Version.parse(tag) ?: continue
            if (best == null || version > best.version) {
                best = Latest(
                    version = version,
                    url = "https://gitee.com/$owner/$repo/releases/tag/$tag",
                    notes = release.string("body").orEmpty(),
                    apkUrl = apkAsset(release),
                )
            }
        }
        return best
    }

    /** tag 兜底同样取最大版本号。 */
    internal fun pickLatestTag(tags: JsonArray): Latest? {
        var best: Latest? = null
        for (item in tags) {
            val tag = runCatching { item.jsonObject }.getOrNull()?.string("name") ?: continue
            val version = Version.parse(tag) ?: continue
            if (best == null || version > best.version) {
                best = Latest(
                    version = version,
                    url = "https://gitee.com/$owner/$repo/releases",
                    notes = "",
                    apkUrl = null,
                )
            }
        }
        return best
    }

    /** 发布里附带的第一个 .apk（Gitee 的附件字段是 `browser_download_url`）。 */
    private fun apkAsset(release: JsonObject): String? {
        val assets = runCatching { release["assets"] as JsonArray }.getOrNull() ?: return null
        return assets.asSequence()
            .mapNotNull { item -> runCatching { item.jsonObject.string("browser_download_url") }.getOrNull() }
            .firstOrNull { it.endsWith(".apk", ignoreCase = true) }
    }

    /** 404 当作「这一类还不存在」，其余异常照常抛出。 */
    private fun getArray(url: String, allowMissing: Boolean): JsonArray {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404 && allowMissing) return JsonArray(emptyList())
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("接口返回 HTTP ${response.code}")
            val element = runCatching { json.parseToJsonElement(text) }.getOrNull()
            return element as? JsonArray ?: JsonArray(emptyList())
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
