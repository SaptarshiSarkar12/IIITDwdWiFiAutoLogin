package com.github.saptarshisarkar12.iiitdwdwifiautologin

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed class LoginResult {
    data class Success(val message: String) : LoginResult()
    data class Failure(val error: String) : LoginResult()
}

object LoginHelper {
    suspend fun performLogin(context: Context, username: String, password: String): LoginResult {
        return withContext(Dispatchers.IO) {
            if (username.isEmpty() || password.isEmpty()) {
                LoginResult.Failure("Failed to decrypt credentials from Keystore.")
            }
            try {
                val url = URL("http://172.16.16.16:8090/login.xml")

                // Prefer routing directly through the Wi-Fi network interface
                // to prevent Mobile Data from intercepting local campus requests
                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val wifiNetwork = connectivityManager.allNetworks.firstOrNull { network ->
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }

                val conn = if (wifiNetwork != null) {
                    wifiNetwork.openConnection(url) as HttpURLConnection
                } else {
                    url.openConnection() as HttpURLConnection
                }

                conn.requestMethod = "POST"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true

                val encUserUrl = URLEncoder.encode(username, "UTF-8")
                val encPassUrl = URLEncoder.encode(password, "UTF-8")
                val payload =
                    "mode=191&username=$encUserUrl&password=$encPassUrl&a=1786430697920&producttype=0"

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val responseText = try {
                    val stream =
                        if (responseCode in 200..299) conn.inputStream else conn.errorStream
                    stream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) {
                    ""
                }
                conn.disconnect()

                val portalMessage = if (responseText.isNotEmpty()) {
                    val match = Regex(
                        "<message>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</message>",
                        RegexOption.DOT_MATCHES_ALL
                    ).find(responseText)
                    match?.groupValues?.get(1)?.trim()
                        ?.replace("{username}", username, ignoreCase = true)
                } else null

                if (responseCode in 200..299) {
                    // Inform Android OS that network connectivity is now working,
                    // prompting it to re-test the connection and dismiss the "Sign in to network" prompt.
                    if (wifiNetwork != null) {
                        connectivityManager.reportNetworkConnectivity(wifiNetwork, true)
                    }

                    val displayMsg = portalMessage?.takeIf { it.isNotBlank() }
                        ?: "Login request sent (HTTP $responseCode)"
                    LoginResult.Success(displayMsg)
                } else {
                    val displayError = portalMessage?.takeIf { it.isNotBlank() }
                        ?: "Gateway error: HTTP $responseCode"
                    LoginResult.Failure(displayError)
                }
                LoginResult.Success("Login Successful")
            } catch (e: Exception) {
                LoginResult.Failure("Failed to connect to gateway: ${e.localizedMessage ?: e.message}")
            }
        }
    }
}
