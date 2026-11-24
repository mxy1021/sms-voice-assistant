package com.mxy1021.smsvoiceassistant.stage2

import android.app.Notification
import android.os.Parcelable
import com.mxy1021.smsvoiceassistant.sms.SpeechFormatter

/** Callback-local only. Only shape() may enter diagnostics. */
internal class WeChatFields(notification: Notification) {
    private val extras = notification.extras
    private val title = extras.getCharSequence(Notification.EXTRA_TITLE).visibleText()
    private val text = extras.getCharSequence(Notification.EXTRA_TEXT).visibleText()
    private val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT).visibleText()
    private val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE).visibleText()
    private val style = extras.getString(Notification.EXTRA_TEMPLATE) == Notification.MessagingStyle::class.java.name
    private val group = if (extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION))
        extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) else null
    private val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
    // Never walk historic messages or fall back to an earlier message in this array.
    private val newest = messages?.lastOrNull()?.let {
        Notification.MessagingStyle.Message.getMessagesFromBundleArray(arrayOf(it)).singleOrNull()
    }
    private val sender = newest?.senderPerson?.name.visibleText()
    private val message = newest?.text.visibleText()
    private val summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
    private val ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
    private val category = notification.category
    private val visibility = notification.visibility
    private val hasPublicVersion = notification.publicVersion != null
    private val hasCustomViews = notification.contentView != null || notification.bigContentView != null

    private fun CharSequence?.visibleText() = this?.toString()?.takeIf { it.isNotBlank() }

    fun shape(): String {
        val kind = when {
            category == Notification.CATEGORY_MESSAGE -> "MESSAGE"
            category == Notification.CATEGORY_CALL -> "CALL"
            category == Notification.CATEGORY_PROGRESS -> "PROGRESS"
            category == Notification.CATEGORY_SERVICE -> "SERVICE"
            category == Notification.CATEGORY_STATUS -> "STATUS"
            category == Notification.CATEGORY_TRANSPORT -> "TRANSPORT"
            category == null -> "UNSPECIFIED"
            else -> "OTHER"
        }
        return "title=${title?.length};text=${text?.length};bigText=${bigText?.length};" +
            "conversation=${conversation?.length};messages=${messages?.size};latestText=${message?.length};" +
            "latestSender=${sender?.length};style=$style;group=$group;category=$kind;" +
            "summary=$summary;ongoing=$ongoing;visibility=$visibility;publicVersion=$hasPublicVersion;customViews=$hasCustomViews"
    }

    class TestSpeech(val text: String, val source: String)

    fun testSpeech(): TestSpeech? {
        if (summary || ongoing || (category != null && category != Notification.CATEGORY_MESSAGE)) return null
        val candidate = if (messages != null || style) {
            if (message != TEST_MESSAGE) return null
            val name = when (group) {
                true -> conversation
                false -> sender
                null -> null
            } ?: return null
            "微信，${name}说：$message"
        } else {
            val label = title ?: return null
            // Allow the exact fixture or a visible prefix ending in a separator.
            // Play the visible string intact. It does not establish sender or group identity.
            val body = listOfNotNull(bigText, text).firstOrNull {
                it == TEST_MESSAGE || it.endsWith("：$TEST_MESSAGE") || it.endsWith(": $TEST_MESSAGE")
            } ?: return null
            "微信，$label，$body"
        }
        val safe = candidate.takeIf {
            it.length <= SpeechFormatter.MAX_FULL_TEXT_CHARS && it.none(Char::isISOControl)
        } ?: return null
        return TestSpeech(safe, when {
            messages == null && !style -> "TITLE_AND_VISIBLE_TEXT"
            group == true -> "STRUCTURED_GROUP"
            else -> "STRUCTURED_PERSON"
        })
    }

    companion object {
        const val TEST_MESSAGE = "测试语音。"
    }
}
