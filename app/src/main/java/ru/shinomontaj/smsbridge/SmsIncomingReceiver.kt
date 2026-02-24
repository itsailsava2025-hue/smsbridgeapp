package ru.shinomontaj.smsbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SmsIncomingReceiver : BroadcastReceiver() {

    private val prefsName = "sms_bridge"

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val baseUrl = (prefs.getString("base_url", "") ?: "").trim().trimEnd('/')
        val token = prefs.getString("device_token", "") ?: ""
        val devId = prefs.getString("device_id", "") ?: ""
        if (baseUrl.isEmpty() || token.isEmpty()) return

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return
        val from = msgs[0]?.originatingAddress ?: return
        val sb = StringBuilder()
        for (m in msgs) sb.append(m?.messageBody ?: "")
        val text = sb.toString()

        val url = "$baseUrl/api/sms-android.php"
        val payload = JSONObject()
        payload.put("op", "device_incoming")
        payload.put("device_token", token)
        payload.put("device_id", devId)
        payload.put("from", from)
        payload.put("text", text)
        payload.put("ts", System.currentTimeMillis())

        val http = OkHttpClient()
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(body).build()

        Thread { 
            try { http.newCall(req).execute().close() } catch (_: Throwable) {}
        }.start()
    }
}
