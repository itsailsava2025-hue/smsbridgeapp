\
package ru.shinomontaj.smsbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.BOOT_COMPLETED") return
        // Авто-старт, если уже привязано
        val prefs = context.getSharedPreferences("sms_bridge", Context.MODE_PRIVATE)
        val token = prefs.getString("device_token", "") ?: ""
        val base = prefs.getString("base_url", "") ?: ""
        if (token.isEmpty() || base.isEmpty()) return
        ContextCompat.startForegroundService(context, Intent(context, BridgeService::class.java))
    }
}
