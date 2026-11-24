package com.mxy1021.smsvoiceassistant.stage2

import android.app.Notification
import android.app.Person
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic platform objects only. Does not post notifications or change grants/settings. */
@RunWith(AndroidJUnit4::class)
class WeChatProbeDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testMessage = WeChatFields.TEST_MESSAGE
    private fun builder() = Notification.Builder(context, "fixture")
        .setSmallIcon(android.R.drawable.ic_dialog_email)

    @Test fun installedListenerAndActivityAreProtected() {
        val flags = PackageManager.ComponentInfoFlags.of(0)
        val listener = context.packageManager.getServiceInfo(
            ComponentName(context, WeChatNotificationListener::class.java), flags
        )
        assertFalse(listener.exported)
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", listener.permission)
        assertEquals(0, listener.foregroundServiceType)
        val activity = context.packageManager.getActivityInfo(
            ComponentName(context, WeChatProbeActivity::class.java), flags
        )
        assertFalse(activity.exported)
    }

    @Test fun plainTitleAndBodyStayVisibleCandidates() {
        val fields = WeChatFields(builder().setContentTitle("示例").setContentText(testMessage).build())
        assertEquals("微信，示例，$testMessage", fields.testSpeech()?.text)
        assertEquals("TITLE_AND_VISIBLE_TEXT", fields.testSpeech()?.source)
        assertTrue(fields.shape().contains("title=2;text=5"))
        assertTrue(fields.shape().contains("group=null"))
        assertFalse(fields.shape().contains("示例"))
        assertFalse(fields.shape().contains(testMessage))
    }

    @Test fun readsBigTextAndLeavesMissingFieldsUnknown() {
        val short = builder().setContentTitle("示例")
            .setStyle(Notification.BigTextStyle().bigText(testMessage))
        assertEquals("TITLE_AND_VISIBLE_TEXT", WeChatFields(short.build()).testSpeech()?.source)
        val hidden = WeChatFields(builder().setContentTitle("固定占位").setVisibility(Notification.VISIBILITY_PRIVATE).build())
        assertTrue(hidden.shape().contains("text=null;bigText=null"))
        assertNull(hidden.testSpeech())
    }

    @Test fun currentStructuredPersonAndGroupAreSeparateSources() {
        val self = Person.Builder().setName("自己").build()
        val sender = Person.Builder().setName("示例").build()
        val direct = Notification.MessagingStyle(self).setGroupConversation(false)
            .addMessage(testMessage, 1, sender)
        val group = Notification.MessagingStyle(self).setGroupConversation(true).setConversationTitle("示例组")
            .addMessage(testMessage, 1, sender)
        val personFields = WeChatFields(builder().setStyle(direct).build())
        assertEquals("STRUCTURED_PERSON", personFields.testSpeech()?.source)
        assertEquals("微信，示例说：$testMessage", personFields.testSpeech()?.text)
        val groupFields = WeChatFields(builder().setStyle(group).build())
        assertEquals("STRUCTURED_GROUP", groupFields.testSpeech()?.source)
        assertEquals("微信，示例组说：$testMessage", groupFields.testSpeech()?.text)
    }

    @Test fun rejectsEarlierOrMalformedLatestMessagesEvenWithFixtureText() {
        val person = Person.Builder().setName("示例").build()
        val style = Notification.MessagingStyle(person).setGroupConversation(false)
            .addMessage(testMessage, 1, person).addMessage("后到样本", 2, person)
        val notification = builder().setStyle(style).build()
        assertNull(WeChatFields(notification).testSpeech())
        val extras = notification.extras
        val valid = extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        assertNotNull(valid)
        extras.putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf(valid!!.first(), Bundle()))
        assertNull(WeChatFields(notification).testSpeech())
    }

    @Test fun rejectsNonMessageSummaryOngoingAndUncontrolledBody() {
        fun sample() = builder().setContentTitle("示例").setContentText(testMessage)
        assertNull(WeChatFields(sample().setCategory(Notification.CATEGORY_CALL).build()).testSpeech())
        assertNull(WeChatFields(sample().setGroup("fixture").setGroupSummary(true).build()).testSpeech())
        assertNull(WeChatFields(sample().setOngoing(true).build()).testSpeech())
        assertNull(WeChatFields(sample().setContentText("其他样本").build()).testSpeech())
    }

    @Test fun enforcesAssemblyLengthAndNeverTruncates() {
        val allowance = 80 - "微信，，$testMessage".length
        fun sample(chars: Int) = WeChatFields(builder().setContentTitle("甲".repeat(chars))
            .setContentText(testMessage).build()).testSpeech()
        assertEquals(80, sample(allowance)?.text?.length)
        assertNull(sample(allowance + 1))
    }
}
