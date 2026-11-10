package com.mxy1021.smsvoiceassistant.audio

import android.os.SystemClock
import android.util.Log
import org.json.JSONObject

/** 仅供阶段 0：只传开发者阶段状态，禁止真实短信、验证码、发送方、设备身份和异常正文。 */
object Stage0Log {
    const val TAG = "SVA_STAGE0"
    fun state(kind: String, field: String, value: String, id: String? = null) {
        val data = JSONObject().put("schema", 1).put("package", "com.mxy1021.smsvoiceassistant")
            .put("epochMs", System.currentTimeMillis()).put("elapsedMs", SystemClock.elapsedRealtime())
            .put("kind", kind).put("field", field).put("value", value)
        if (id != null) data.put("id", id)
        Log.i(TAG, data.toString())
    }
}
