package com.github.saptarshisarkar12.iiitdwdwifiautologin

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestPermissions()
        setupNetworkListener()

        val sharedPreferences = getSharedPreferences("secure_login_prefs", MODE_PRIVATE)
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        val savedUser = sharedPreferences.getString("username", "") ?: ""
        val savedPass = sharedPreferences.getString("password", "") ?: ""

        if (savedUser.isNotEmpty()) etUsername.setText(KeystoreHelper.decrypt(savedUser))
        if (savedPass.isNotEmpty()) etPassword.setText(KeystoreHelper.decrypt(savedPass))

        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (!saveCredentialsIfValid(sharedPreferences, user, pass)) return@setOnClickListener

            btnLogin.isEnabled = false
            Toast.makeText(this, "Attempting login...", Toast.LENGTH_SHORT).show()

            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)

            lifecycleScope.launch {
                val result = LoginHelper.performLogin(this@MainActivity, user, pass)
                btnLogin.isEnabled = true
                when (result) {
                    is LoginResult.Success -> Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                    is LoginResult.Failure -> Toast.makeText(this@MainActivity, result.error, Toast.LENGTH_LONG).show()
                }
            }.start()
        }
    }

    private fun saveCredentialsIfValid(sharedPreferences: SharedPreferences, user: String, pass: String): Boolean {
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
            return false
        }
        sharedPreferences.edit {
            putString("username", KeystoreHelper.encrypt(user))
            putString("password", KeystoreHelper.encrypt(pass))
        }
        return true
    }

    private fun checkAndRequestPermissions() {
        val fineLoc =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fineLoc != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        } else {
            val bgLoc = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
            if (bgLoc != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    2
                )
            }
        }
    }

    private fun setupNetworkListener() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        // clearCapabilities() ensures the callback fires for captive portals
        val request = NetworkRequest.Builder()
            .clearCapabilities()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val intent = Intent(this, WifiReceiver::class.java).apply {
            action = "com.github.saptarshisarkar12.iiitdwdwifiautologin.WIFI_TRIGGER"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        cm.registerNetworkCallback(request, pendingIntent)
    }
}
