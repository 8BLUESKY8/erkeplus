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

    /** 旧系统（Android 10 及以下）的回收站子目录。 */
    const val TRASH_FOLDER = ".trash"

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

    /** 列出已下载文件，按修改时间倒序；默认不含回收站内容。 */
    fun list(context: Context): List<LocalFile> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listMediaStore(context, trashed = false) else listLegacy()

    /** 回收站里的文件。Android 11+ 用系统回收站，Android 10 及以下用 `Download/ErkePlus/.trash/`。 */
    fun listTrash(context: Context): List<LocalFile> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> listMediaStore(context, trashed = true)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> listMediaStore(context, trashed = true)
        else -> listLegacyTrash()
    }

    /** 两种回收站都支持，只是实现不同。 */
    fun trashSupported(): Boolean = true

    /** 是否使用系统回收站（Android 11+）。 */
    fun usesSystemTrash(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** 移到回收站；Android 11+ 打系统标记，更旧的系统把文件挪进 `.trash` 子目录。 */
    fun trash(context: Context, file: LocalFile): Boolean {
        if (usesSystemTrash()) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }
            return runCatching { context.contentResolver.update(file.uri, values, null, null) > 0 }
                .getOrDefault(false)
        }
        return runCatching {
            val bytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                ?: return false
            moveInto(context, file.name, file.mime, bytes)
            delete(context, file)
            true
        }.getOrDefault(false)
    }

    /** 从回收站还原。 */
    fun restore(context: Context, file: LocalFile): Boolean {
        if (usesSystemTrash()) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }
            return runCatching { context.contentResolver.update(file.uri, values, null, null) > 0 }
                .getOrDefault(false)
        }
        return runCatching {
            val bytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                ?: return false
            write(context, file.name, file.mime) { output -> output.write(bytes) }
            delete(context, file)
            true
        }.getOrDefault(false)
    }

    /** 旧系统：把字节写进 `.trash` 子目录。 */
    private fun moveInto(context: Context, name: String, mime: String, bytes: ByteArray): LocalFile {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return write(context, name, mime, subFolder = TRASH_FOLDER) { it.write(bytes) }
        }
        val dir = File(legacyDir(), TRASH_FOLDER).apply { mkdirs() }
        val file = File(dir, safeName(name))
        file.writeBytes(bytes)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        return LocalFile(
            name = file.name,
            uri = Uri.fromFile(file),
            size = file.length(),
            modified = file.lastModified(),
            mime = mime,
        )
    }

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
        subFolder: String = "",
        writer: (java.io.OutputStream) -> Unit,
    ): LocalFile {
        val fileName = safeName(name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeMediaStore(context, fileName, mime, subFolder, writer)
        }
        return writeLegacy(context, fileName, mime, subFolder, writer)
    }

    private fun safeName(name: String): String {
        val cleaned = name.substringAfterLast('/').replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "attachment-${System.currentTimeMillis()}" }
    }

    private fun writeMediaStore(
        context: Context,
        name: String,
        mime: String,
        subFolder: String,
        writer: (java.io.OutputStream) -> Unit,
    ): LocalFile {
        val resolver = context.contentResolver
        val relativePath = buildString {
            append(Environment.DIRECTORY_DOWNLOADS).append(File.separator).append(FOLDER)
            if (subFolder.isNotBlank()) append(File.separator).append(subFolder)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
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
        subFolder: String,
        writer: (java.io.OutputStream) -> Unit,
    ): LocalFile {
        val dir = (if (subFolder.isBlank()) legacyDir() else File(legacyDir(), subFolder)).apply { mkdirs() }
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

    private fun listMediaStore(context: Context, trashed: Boolean): List<LocalFile> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val files = mutableListOf<LocalFile>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+：回收站内容默认不返回，必须用 MATCH_TRASHED 查询参数显式要
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE ?"
            val args = arrayOf("%$FOLDER%", "%/$TRASH_FOLDER%")
            val queryArgs = android.os.Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putString(
                    android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
                )
                putInt(
                    MediaStore.QUERY_ARG_MATCH_TRASHED,
                    if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE,
                )
            }
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                queryArgs,
                null,
            )?.use { cursor -> files += readFiles(cursor) }
            return files
        }

        // Android 10：没有 IS_TRASHED，回收站就是 `.trash` 子目录
        val selection = if (trashed) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE ?"
        }
        val args = if (trashed) {
            arrayOf("%$FOLDER/$TRASH_FOLDER%")
        } else {
            arrayOf("%$FOLDER%", "%/$TRASH_FOLDER%")
        }
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor -> files += readFiles(cursor) }
        return files
    }

    private fun readFiles(cursor: android.database.Cursor): List<LocalFile> {
        val result = mutableListOf<LocalFile>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val name = cursor.getString(1).orEmpty()
            val size = cursor.getLong(2)
            val modified = cursor.getLong(3) * 1000
            val mime = cursor.getString(4) ?: mimeOf(name)
            result += LocalFile(
                name = name,
                uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                size = size,
                modified = modified,
                mime = mime,
            )
        }
        return result
    }

    private fun listLegacy(): List<LocalFile> =
        listDir(legacyDir())

    private fun listLegacyTrash(): List<LocalFile> =
        listDir(File(legacyDir(), TRASH_FOLDER))

    private fun listDir(dir: File): List<LocalFile> {
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
