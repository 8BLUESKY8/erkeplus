package gitee.leolin_me.erkepp.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import coil.Coil
import gitee.leolin_me.erkepp.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 「数据管理」。
 *
 * 把应用里会自己长大的东西列清楚：缓存、日志、下载过的更新包、附件回收站。
 * 每一项都能单独勾选清理，默认只勾影响不大的（清了会自动重来）。
 *
 * 大小是现场扫盘的，不缓存——缓存本身就是为了随时被清。
 */
enum class StorageItem(
    val key: String,
    val title: String,
    val detail: String,
    /** 清掉有可见后果（要重新登录网页版、文件再也找不回）。 */
    val risky: Boolean,
    /** 第一次进来默认勾选：只勾影响不大的。 */
    val checkedByDefault: Boolean,
) {
    AVATAR(
        key = "avatar",
        title = "头像缓存",
        detail = "榜单和资料页的头像图片，清掉后按需重新下载",
        risky = false,
        checkedByDefault = true,
    ),
    COVER(
        key = "cover",
        title = "封面缓存",
        detail = "活动封面图，清掉后重新加载",
        risky = false,
        checkedByDefault = true,
    ),
    WEB_CACHE(
        key = "web_cache",
        title = "网页缓存",
        detail = "官方网页版的临时文件，清掉后首次打开会慢一点",
        risky = false,
        checkedByDefault = true,
    ),
    LOGS(
        key = "logs",
        title = "运行日志",
        detail = "排查问题用的日志文件",
        risky = false,
        checkedByDefault = true,
    ),
    UPDATE(
        key = "update",
        title = "更新安装包",
        detail = "下载过的更新包，安装完成后就没用了",
        risky = false,
        checkedByDefault = true,
    ),
    TEMP(
        key = "temp",
        title = "其他临时文件",
        detail = "崩溃报告之类的临时目录",
        risky = false,
        checkedByDefault = true,
    ),
    WEB_STORAGE(
        key = "web_storage",
        title = "网页版本地数据",
        detail = "登录态与网页设置，清掉后网页版要重新登录",
        risky = true,
        checkedByDefault = false,
    ),
    TRASH(
        key = "attach_trash",
        title = "附件回收站",
        detail = "在「我的附件」里删掉的文件，清掉后无法恢复",
        risky = true,
        checkedByDefault = false,
    ),
    ;

    companion object {
        fun of(key: String): StorageItem? = entries.firstOrNull { it.key == key }

        /** 第一次进来该勾上的那些。 */
        val defaultSelection: Set<String> = entries.filter { it.checkedByDefault }.map { it.key }.toSet()
    }
}

/** 一项的扫描结果。[count] 是目录里的文件数；回收站按文件条数算。 */
data class StorageEntry(
    val item: StorageItem,
    val bytes: Long,
    val count: Int,
)

data class StorageReport(
    val appBytes: Long,
    val entries: List<StorageEntry>,
) {
    fun bytesOf(items: Collection<StorageItem>): Long =
        entries.filter { it.item in items }.sumOf { it.bytes }

    fun entryOf(item: StorageItem): StorageEntry? = entries.firstOrNull { it.item == item }
}

class StorageRepository {

    suspend fun scan(context: Context): StorageReport = withContext(Dispatchers.IO) {
        val cache = context.cacheDir
        val known = setOf("avatar", "image_cache", "WebView", "update")
        val leftovers = (cache.listFiles() ?: emptyArray()).filter { it.name !in known }
        val entries = listOf(
            dirEntry(StorageItem.AVATAR, File(cache, "avatar")),
            dirEntry(StorageItem.COVER, File(cache, "image_cache")),
            dirEntry(StorageItem.WEB_CACHE, File(cache, "WebView")),
            dirEntry(StorageItem.LOGS, File(context.filesDir, "logs")),
            dirEntry(StorageItem.UPDATE, File(cache, "update")),
            StorageEntry(
                item = StorageItem.TEMP,
                bytes = leftovers.sumOf { sizeOf(it) },
                count = leftovers.sumOf { fileCount(it) },
            ),
            webStorageEntry(context),
            trashEntry(context),
        )
        // 应用数据合计：dataDir 底下的一切（缓存 + 数据库 + 网页数据）
        StorageReport(appBytes = sizeOf(context.dataDir), entries = entries)
    }

    /**
     * 清理勾中的项，返回实际腾出来的字节数。
     *
     * 勾了就按这块地方整个清掉；网页版本地数据要先走 WebView 的接口清登录态，
     * 再删它留下的文件。
     */
    suspend fun clear(context: Context, items: Collection<StorageItem>): Long {
        if (items.isEmpty()) return 0
        val before = withContext(Dispatchers.IO) { sizeOf(context.dataDir) }
        for (item in items) {
            when (item) {
                StorageItem.AVATAR -> deleteDir(File(context.cacheDir, "avatar"))
                StorageItem.COVER -> clearCoverCache(context)
                StorageItem.WEB_CACHE -> deleteDir(File(context.cacheDir, "WebView"))
                StorageItem.LOGS -> deleteDir(File(context.filesDir, "logs"))
                StorageItem.UPDATE -> deleteDir(File(context.cacheDir, "update"))
                StorageItem.TEMP -> clearTemp(context)
                StorageItem.WEB_STORAGE -> clearWebStorage(context)
                StorageItem.TRASH -> clearTrash(context)
            }
            AppLog.d("storage", "clear ${item.key} -> ${item.title}")
        }
        val after = withContext(Dispatchers.IO) { sizeOf(context.dataDir) }
        return (before - after).coerceAtLeast(0)
    }

    private fun dirEntry(item: StorageItem, dir: File): StorageEntry =
        StorageEntry(item = item, bytes = sizeOf(dir), count = fileCount(dir))

    private fun webStorageEntry(context: Context): StorageEntry {
        val dirs = listOf(
            File(context.dataDir, "app_webview"),
            File(context.noBackupFilesDir, ".webview"),
        )
        return StorageEntry(
            item = StorageItem.WEB_STORAGE,
            bytes = dirs.sumOf { sizeOf(it) },
            count = dirs.sumOf { fileCount(it) },
        )
    }

    private fun trashEntry(context: Context): StorageEntry {
        val files = runCatching { AttachmentStorage.listTrash(context) }.getOrDefault(emptyList())
        return StorageEntry(
            item = StorageItem.TRASH,
            bytes = files.sumOf { it.size },
            count = files.size,
        )
    }

    private fun clearCoverCache(context: Context) {
        runCatching {
            Coil.imageLoader(context).let { loader ->
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
        }
        // 有些构建（比如调试包自己设了 ImageLoader）磁盘目录可能对不上，兜底再删一次目录
        deleteDir(File(context.cacheDir, "image_cache"))
    }

    private fun clearTemp(context: Context) {
        val cache = context.cacheDir
        val keep = setOf("avatar", "image_cache", "WebView", "update")
        cache.listFiles()?.forEach { if (it.name !in keep) deleteDir(it) }
    }

    private suspend fun clearWebStorage(context: Context) {
        // Cookie 和 localStorage 只能在主线程调
        withContext(Dispatchers.Main) {
            runCatching { CookieManager.getInstance().removeAllCookies(null) }
            runCatching { WebStorage.getInstance().deleteAllData() }
        }
        // 接口清不掉的落盘文件（IndexedDB、缓存目录）直接删，下次打开网页版会重建
        deleteDir(File(context.dataDir, "app_webview"))
        deleteDir(File(context.noBackupFilesDir, ".webview"))
    }

    private fun clearTrash(context: Context) {
        runCatching { AttachmentStorage.listTrash(context) }.getOrDefault(emptyList())
            .forEach { runCatching { AttachmentStorage.delete(context, it) } }
    }

    private fun deleteDir(dir: File) {
        if (!dir.exists()) return
        runCatching { dir.deleteRecursively() }
    }

    private fun sizeOf(dir: File): Long {
        if (!dir.exists()) return 0
        if (dir.isFile) return dir.length()
        return runCatching {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0)
    }

    private fun fileCount(dir: File): Int {
        if (!dir.exists()) return 0
        if (dir.isFile) return 1
        return runCatching { dir.walkTopDown().count { it.isFile } }.getOrDefault(0)
    }
}

/** 体积文案：1024 进制，保留一位小数，最大到 GB。 */
internal fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = 1024.0
    return when {
        bytes < kb -> "$bytes B"
        bytes < kb * kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        bytes < kb * kb * kb -> String.format(Locale.US, "%.1f MB", bytes / (kb * kb))
        else -> String.format(Locale.US, "%.1f GB", bytes / (kb * kb * kb))
    }
}
