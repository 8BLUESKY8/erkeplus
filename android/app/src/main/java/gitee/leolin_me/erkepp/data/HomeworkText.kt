package gitee.leolin_me.erkepp.data

/**
 * 作业正文里服务端存不下的字符。
 *
 * 实测（2026-10-24）：正文带 `🥰` 这类 **4 字节 UTF-8** 字符时，`activity/submit/work_3_0_1`
 * 返回 `code=10000 出现未知数据错误`——服务端的内容列是 `utf8` 而不是 `utf8mb4`，写不进去。
 * BMP 内的符号（`❤️` = U+2764 + U+FE0F）不受影响。
 *
 * Kotlin 里 4 字节字符在字符串中表现为代理对，判断高代理即可；这里不猜具体 emoji 名单，
 * 免得漏掉新表情。
 */
fun hasTooWideChar(text: String): Boolean = text.any { it.isHighSurrogate() }

/** 去掉 4 字节字符（连同它的低代理），用于「替我清掉再提交」。 */
fun stripTooWideChars(text: String): String {
    val builder = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
            i += 2
            continue
        }
        builder.append(c)
        i++
    }
    return builder.toString()
}
