package gitee.leolin_me.erkepp.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import gitee.leolin_me.erkepp.AppLog
import android.media.MediaScannerConnection
import java.io.File

/**
 * 附件的本地存放。
 *
 * 统一放在公共下载目录 `Download/ErkePlus/`：
 * - Android 10 及以上走 MediaStore，不需要任何权限；
 * - Android 9 及以下直接写文件，需要声明为 `maxSdkVersion=28` 的写外部存储权限。
 */
object AttachmentStorage {

    const val FOLDER = "ErkePlus"

    /** 已下载的文件。[uri] 可直接给系统应用打开或分享。 */
    data class LocalFile(
        val name: String,
        val uri: Uri,
        val size: Long,
        val modified: Long,
        val mime: String,
    )

    /** 目录在旧系统上的绝对路径；新系统返回 null（走 MediaStore）。 */
    private fun legacyDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)

    fun mimeOf(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    /** 列出已下载文件，按修改时间倒序。 */
    fun list(context: Context): List<LocalFile> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listMediaStore(context) else listLegacy()

    fun find(context: Context, name: String): LocalFile? =
        list(context).firstOrNull { it.name == name }

    /** 旧系统给的是 file:// uri，交给系统应用前要换成 FileProvider 的 content://。 */
    fun shareableUri(context: Context, file: LocalFile): Uri {
        if (file.uri.scheme == "file") {
            val path = file.uri.path ?: return file.uri
            return runCatching {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.files",
                    File(path),
                )
            }.getOrDefault(file.uri)
        }
        return file.uri
    }

    fun delete(context: Context, file: LocalFile): Boolean = runCatching {
        context.contentResolver.delete(file.uri, null, null) >= 0
    }.getOrDefault(false)

    /**
     * 写一个文件进下载目录。[writer] 拿到输出流后自己往里写。
     * 返回写入后的本地文件；失败抛异常。
     */
    fun write(
        context: Context,
        name: String,
        mime: String,
        writer: (java.io.OutputStream) -> Unit,
    ): LocalFile {
        val fileName = safeName(name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeMediaStore(context, fileName, mime, writer)
        }
        return writeLegacy(context, fileName, mime, writer)
    }

    private fun safeName(name: String): String {
        val cleaned = name.substringAfterLast('/').replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "attachment-${System.currentTimeMillis()}" }
    }

    private fun writeMediaStore(
        context: Context,
        name: String,
        mime: String,
        writer: (java.io.OutputStream) -> Unit,
    ): LocalFile {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + File.separator + FOLDER,
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法在下载目录创建文件")
        try {
            resolver.openOutputStream(uri)?.use(writer) ?: error("无法打开输出流")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        AppLog.d("storage", "saved $name -> $uri")
        return LocalFile(
            name = name,
            uri = uri,
            size = fileSize(context, uri),
            modified = System.currentTimeMillis(),
            mime = mime,
        )
    }

    private fun writeLegacy(
        context: Context,
        name: String,
        mime: String,
        writer: (java.io.OutputStream) -> Unit,
    ): LocalFile {
        val dir = legacyDir().apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use(writer)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        return LocalFile(
            name = name,
            uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file,
            ),
            size = file.length(),
            modified = file.lastModified(),
            mime = mime,
        )
    }

    private fun listMediaStore(context: Context): List<LocalFile> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%${FOLDER}%")
        val files = mutableListOf<LocalFile>()
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1).orEmpty()
                val size = cursor.getLong(2)
                val modified = cursor.getLong(3) * 1000
                val mime = cursor.getString(4) ?: mimeOf(name)
                files += LocalFile(
                    name = name,
                    uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                    size = size,
                    modified = modified,
                    mime = mime,
                )
            }
        }
        return files
    }

    private fun listLegacy(): List<LocalFile> {
        val dir = legacyDir()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                LocalFile(
                    name = file.name,
                    // 旧系统在 repository 层换成 FileProvider 的 uri
                    uri = Uri.fromFile(file),
                    size = file.length(),
                    modified = file.lastModified(),
                    mime = mimeOf(file.name),
                )
            }
            .orEmpty()
    }

    private fun fileSize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    }.getOrDefault(0L)
}
