package ru.shinomontaj.smsbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class BridgeService : Service() {

    private val prefs by lazy { getSharedPreferences("sms_bridge", MODE_PRIVATE) }
    private val http = OkHttpClient()

    @Volatile private var running = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildNotif("SMS мост запущен"))
        running = true
        Thread { loop() }.start()
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("sms_bridge", "SMS Bridge", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification {
        return NotificationCompat.Builder(this, "sms_bridge")
            .setContentTitle("Shinomontaj SMS Bridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setOngoing(true)
            .build()
    }

    private fun loop() {
        val baseUrl = (prefs.getString("base_url", "") ?: "").trim().trimEnd('/')
        val token = prefs.getString("device_token", "") ?: ""
        val devId = prefs.getString("device_id", "") ?: ""
        if (baseUrl.isEmpty() || token.isEmpty()) return

        while (running) {
            try {
                pollAndSend(baseUrl, token, devId)
            } catch (_: Throwable) { }
            try { Thread.sleep(2500) } catch (_: Throwable) {}
        }
    }

    private fun pollAndSend(baseUrl: String, token: String, devId: String) {
        val url = "$baseUrl/api/sms-android.php"
        val payload = JSONObject()
        payload.put("op", "device_poll")
        payload.put("device_token", token)
        payload.put("device_id", devId)

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(body).build()

        http.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return
            val j = JSONObject(txt)
            val items = j.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                val outboxId = it.optInt("outbox_id", 0)
                val phone = it.optString("phone_norm", "")
                val text = it.optString("text", "")
                if (outboxId <= 0 || phone.isEmpty() || text.isEmpty()) continue
                sendSms(baseUrl, token, devId, outboxId, phone, text)
            }
        }
    }

    private fun sendSms(baseUrl: String, token: String, devId: String, outboxId: Int, phone: String, text: String) {
        try {
            val sm = SmsManager.getDefault()
            val sentIntent = PendingIntent.getBroadcast(
                this,
                outboxId,
                Intent(this, SmsStatusReceiver::class.java)
                    .setAction("SMS_SENT")
                    .putExtra("outbox_id", outboxId)
                    .putExtra("device_token", token)
                    .putExtra("base_url", baseUrl)
                    .putExtra("device_id", devId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            sm.sendTextMessage("+" + phone, null, text, sentIntent, null)
        } catch (e: Throwable) {
            ack(baseUrl, token, devId, outboxId, "failed", e.message ?: "send error")
        }
    }

    private fun ack(baseUrl: String, token: String, devId: String, outboxId: Int, status: String, error: String) {
        val url = "$baseUrl/api/sms-android.php"
        val payload = JSONObject()
        payload.put("op", "device_ack")
        payload.put("device_token", token)
        payload.put("device_id", devId)
        payload.put("outbox_id", outboxId)
        payload.put("status", status)
        payload.put("error", error)

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(body).build()
        try { http.newCall(req).execute().close() } catch (_: Throwable) {}
    }
}
