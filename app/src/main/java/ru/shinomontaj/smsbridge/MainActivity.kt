package ru.shinomontaj.smsbridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("sms_bridge", MODE_PRIVATE) }
    private val http = OkHttpClient()

    private lateinit var tvStatus: TextView

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val txt = result?.contents ?: return@registerForActivityResult
        // Expect: SMBR1|https://shinomontaj.online|PAIRCODE
        val parts = txt.split("|")
        if (parts.size >= 3 && parts[0].startsWith("SMBR1")) {
            val baseUrl = parts[1].trim().trimEnd('/')
            val code = parts[2].trim()
            doRegister(baseUrl, code)
        } else {
            // fallback: maybe plain code
            val baseUrl = guessBaseUrl()
            if (baseUrl.isNotEmpty()) doRegister(baseUrl, txt.trim())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnScan).setOnClickListener { startQrScan() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startBridge() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopBridge() }

        ensurePermissions()
        refreshStatus()
    }

    private fun ensurePermissions() {
        val perms = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        val need = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 1001)
        }
    }

    private fun refreshStatus() {
        val token = prefs.getString("device_token", "") ?: ""
        val baseUrl = prefs.getString("base_url", "") ?: ""
        val devId = prefs.getString("device_id", "") ?: ""
        if (token.isNotEmpty() && baseUrl.isNotEmpty()) {
            tvStatus.text = "✅ Привязано\nbase: $baseUrl\nid: $devId"
        } else {
            tvStatus.text = "❌ Не привязано\nОткрой в CRM «SMS Android» → «Привязать телефон» → сканируй QR."
        }
    }

    private fun startQrScan() {
        val opt = ScanOptions()
        opt.setPrompt("Сканируй QR из кабинета партнёра")
        opt.setBeepEnabled(true)
        opt.setOrientationLocked(false)
        scanLauncher.launch(opt)
    }

    private fun guessBaseUrl(): String {
        // If already paired earlier, reuse
        return prefs.getString("base_url", "")?.trim()?.trimEnd('/') ?: ""
    }

    private fun deviceId(): String {
        var id = prefs.getString("device_id", "") ?: ""
        if (id.isEmpty()) {
            id = try {
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            } catch (_: Throwable) { "" }
            if (id.isEmpty()) id = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    private fun appVersionName(): String {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.versionName ?: "1.0"
        } catch (_: Throwable) {
            "1.0"
        }
    }

    private fun doRegister(baseUrl: String, code: String) {
        val devId = deviceId()
        val url = "$baseUrl/api/sms-android.php"
        val payload = JSONObject()
        payload.put("op", "device_register")
        payload.put("code", code)
        payload.put("device_id", devId)
        payload.put("model", android.os.Build.MODEL ?: "")
        payload.put("app_version", appVersionName())

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(body).build()

        Thread {
            try {
                val resp = http.newCall(req).execute()
                val txt = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: $txt")
                val j = JSONObject(txt)
                if (j.optInt("ok", 0) != 1) throw RuntimeException(j.optString("message", "register failed"))
                val token = j.optString("device_token", "")
                prefs.edit()
                    .putString("base_url", baseUrl)
                    .putString("device_token", token)
                    .apply()
                runOnUiThread { refreshStatus() }
            } catch (e: Throwable) {
                runOnUiThread { tvStatus.text = "❌ Ошибка привязки: ${e.message}" }
            }
        }.start()
    }

    private fun startBridge() {
        val token = prefs.getString("device_token", "") ?: ""
        val baseUrl = prefs.getString("base_url", "") ?: ""
        if (token.isEmpty() || baseUrl.isEmpty()) {
            tvStatus.text = "Сначала привяжи телефон (скан QR)"
            return
        }
        val it = Intent(this, BridgeService::class.java)
        ContextCompat.startForegroundService(this, it)
        refreshStatus()
    }

    private fun stopBridge() {
        stopService(Intent(this, BridgeService::class.java))
        refreshStatus()
    }
}
