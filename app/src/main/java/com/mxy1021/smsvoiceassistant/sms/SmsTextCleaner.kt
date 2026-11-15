package com.mxy1021.smsvoiceassistant.sms

/** Speech copy only. Chinese punctuation terminates a URL rather than consuming the next clause. */
object SmsTextCleaner {
    private val urls = Regex(
        "(?i)(?:https?://|www\\.)[^\\s<>\\[\\]{}\\p{IsHan}，。！？；：、‘’“”（）,;\"']+|" +
            "(?<![A-Za-z0-9_@./-])(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+" +
            "(?:com|cn|net|org|edu|gov|io|app|co|me|info|biz)(?![a-z0-9])" +
            "(?::[0-9]+)?(?:[/#?][^\\s<>\\[\\]{}\\p{IsHan}，。！？；：、‘’“”（）,;\"']*)?"
    )
    fun maskUrls(body: String): String = urls.replace(body) { " ".repeat(it.value.length) }
    fun clean(body: String): String {
        val removed = urls.replace(body, " ")
        return removed
            .replace(Regex("[（(]\\s*[）)]"), "")
            .replace(Regex("(?:详情|查看详情|点击查看|链接|网址)[：:]\\s*(?=[，。！？；;!?]|$)"), "")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([，。！？；：、,.;!?])"), "$1")
            .replace(Regex("([，。！？；,.;!?])(?:[，。！？；,.;!?])+"), "$1")
            .trim().trimStart('，', ',', '；', ';').trimEnd('，', ',', '；', ';', '：', ':').trim()
    }
}
