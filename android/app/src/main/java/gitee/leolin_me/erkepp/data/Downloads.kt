package gitee.leolin_me.erkepp.data

import android.content.Context
import gitee.leolin_me.erkepp.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 附件下载队列。
 *
 * 生命周期跟着进程走：离开页面、切到别的标签都不会中断，只有杀进程才停。
 * 进度用 StateFlow 暴露，附件列表和预览页都直接订阅。
 */
object Downloads {

    enum class State { QUEUED, RUNNING, DONE, FAILED, CANCELED }

    data class Task(
        val url: String,
        val name: String,
        val mime: String,
        val expectedSize: Long,
        val done: Long,
        val total: Long,
        val state: State,
        val error: String? = null,
    ) {
        val key: String get() = url
        val progress: Float
            get() = when {
                total > 0 -> (done.toFloat() / total).coerceIn(0f, 1f)
                state == State.DONE -> 1f
                else -> 0f
            }
        val sizeText: String get() = formatSize(if (total > 0) total else expectedSize)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _tasks = MutableStateFlow<Map<String, Task>>(emptyMap())
    val tasks: StateFlow<Map<String, Task>> = _tasks

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 已经在下载或已下完就复用，不重复入队。 */
    fun enqueue(context: Context, url: String, name: String, expectedSize: Long = 0L): Task {
        val existing = _tasks.value[url]
        if (existing != null && existing.state in setOf(State.QUEUED, State.RUNNING, State.DONE)) {
            return existing
        }
        val app = context.applicationContext
        val mime = AttachmentStorage.mimeOf(name)
        val task = Task(
            url = url,
            name = name,
            mime = mime,
            expectedSize = expectedSize,
            done = 0,
            total = expectedSize,
            state = State.QUEUED,
        )
        update(url) { task }
        jobs[url] = scope.launch { run(app, task) }
        AppLog.d("download", "enqueue $name size=$expectedSize")
        return task
    }

    fun cancel(url: String) {
        jobs[url]?.cancel()
        jobs.remove(url)
        update(url) { it.copy(state = State.CANCELED) }
    }

    fun retry(context: Context, url: String, name: String, expectedSize: Long) {
        jobs.remove(url)
        enqueue(context, url, name, expectedSize)
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filterValues { it.state in setOf(State.QUEUED, State.RUNNING) }
    }

    private suspend fun run(context: Context, task: Task) {
        update(task.url) { it.copy(state = State.RUNNING) }
        runCatching {
            val request = Request.Builder().url(task.url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("响应为空")
                val total = body.contentLength().takeIf { it > 0 } ?: task.expectedSize
                update(task.url) { it.copy(total = total) }
                val saved = AttachmentStorage.write(context, task.name, task.mime) { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            update(task.url) { it.copy(done = written) }
                        }
                    }
                }
                update(task.url) { it.copy(state = State.DONE, done = saved.size, total = saved.size) }
                AppLog.d("download", "done ${task.name} ${saved.size} bytes")
            }
        }.onFailure { error ->
            if (error is CancellationException) {
                update(task.url) { it.copy(state = State.CANCELED) }
            } else {
                AppLog.d("download", "fail ${task.name} ${error.message}")
                update(task.url) { it.copy(state = State.FAILED, error = error.message ?: "下载失败") }
            }
        }
        jobs.remove(task.url)
    }

    private fun update(url: String, transform: (Task) -> Task) {
        _tasks.value = _tasks.value.toMutableMap().apply {
            val current = get(url) ?: return@apply
            put(url, transform(current))
        }
    }

    private fun update(url: String, task: Task) {
        _tasks.value = _tasks.value.toMutableMap().apply { put(url, task) }
    }

    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "未知大小"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    }

    const val LARGE_FILE_BYTES = 5L * 1024 * 1024
}
