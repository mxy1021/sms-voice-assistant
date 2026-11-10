package com.mxy1021.smsvoiceassistant.audio

import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.util.UUID

/** 仅供阶段 0：只传开发者阶段状态，禁止真实短信、验证码、发送方、设备身份和异常正文。 */
object Stage0Log {
    const val TAG = "SVA_STAGE0"
    val processRun: String = UUID.randomUUID().toString().take(8)
    val processStartElapsed: Long = SystemClock.elapsedRealtime()
    private fun base(kind: String, field: String, value: String, id: String?): JSONObject {
        val data = JSONObject().put("schema", 1).put("package", "com.mxy1021.smsvoiceassistant")
            .put("epochMs", System.currentTimeMillis()).put("elapsedMs", SystemClock.elapsedRealtime())
            .put("processRun", processRun).put("processStartElapsed", processStartElapsed)
            .put("kind", kind).put("field", field).put("value", value)
        if (id != null) data.put("id", id)
        return data
    }
    fun state(kind: String, field: String, value: String, id: String? = null) {
        Log.i(TAG, base(kind, field, value, id).toString())
    }
    fun timing(id: String?, stage: String, atElapsed: Long, receiveElapsed: Long?) {
        val data = base("timing", stage, "时间点", id).put("pointElapsedMs", atElapsed)
            .put("dispatchLagMs", SystemClock.elapsedRealtime() - atElapsed)
        if (receiveElapsed != null) data.put("sinceReceiveMs", atElapsed - receiveElapsed)
        Log.i(TAG, data.toString())
    }
}
