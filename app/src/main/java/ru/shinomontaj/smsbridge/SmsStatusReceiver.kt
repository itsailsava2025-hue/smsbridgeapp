package ru.shinomontaj.smsbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "SMS_SENT") return

        val outboxId = intent.getIntExtra("outbox_id", 0)
        val token = intent.getStringExtra("device_token") ?: ""
        val baseUrl = (intent.getStringExtra("base_url") ?: "").trim().trimEnd('/')
        val devId = intent.getStringExtra("device_id") ?: ""
        if (outboxId <= 0 || token.isEmpty() || baseUrl.isEmpty()) return

        val status = if (resultCode == android.app.Activity.RESULT_OK) "sent" else "failed"

        val payload = JSONObject()
        payload.put("op", "device_ack")
        payload.put("device_token", token)
        payload.put("device_id", devId)
        payload.put("outbox_id", outboxId)
        payload.put("status", status)
        payload.put("error", if (status == "failed") "send failed ($resultCode)" else "")

        val http = OkHttpClient()
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/sms-android.php").post(body).build()

        Thread { 
            try { http.newCall(req).execute().close() } catch (_: Throwable) {}
        }.start()
    }
}
