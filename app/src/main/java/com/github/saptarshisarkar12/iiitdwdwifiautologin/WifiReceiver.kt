package com.github.saptarshisarkar12.iiitdwdwifiautologin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WifiReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)

                val wifiInfo = capabilities?.transportInfo as? WifiInfo
                val ssid = wifiInfo?.ssid?.replace("\"", "")
                    ?: getLegacySsid(context)

                if (ssid == "IIIT DHARWAD-STUDENT") {
                    performAutoLogin(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getLegacySsid(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.connectionInfo?.ssid?.replace("\"", "")
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun performAutoLogin(context: Context) {
        val prefs = context.getSharedPreferences("secure_login_prefs", Context.MODE_PRIVATE)
        val encUser = prefs.getString("username", "") ?: ""
        val encPass = prefs.getString("password", "") ?: ""

        if (encUser.isEmpty() || encPass.isEmpty()) return

        val username = KeystoreHelper.decrypt(encUser)
        val password = KeystoreHelper.decrypt(encPass)

        if (username.isNotEmpty() && password.isNotEmpty()) {
            LoginHelper.performLogin(context, username, password)
        }
    }
}
