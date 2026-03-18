package com.proxyshare.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.InetAddress

class MainActivity : AppCompatActivity() {
    private var proxyServer: ProxyServer? = null
    private var isRunning = false
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvIp: TextView
    private lateinit var tvPort: TextView
    private lateinit var tvConnections: TextView
    private lateinit var tvHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnToggle = findViewById(R.id.btnToggle)
        tvStatus = findViewById(R.id.tvStatus)
        tvIp = findViewById(R.id.tvIp)
        tvPort = findViewById(R.id.tvPort)
        tvConnections = findViewById(R.id.tvConnections)
        tvHint = findViewById(R.id.tvHint)
        btnToggle.setOnClickListener { if (isRunning) stopProxy() else startProxy() }
        tvIp.setOnClickListener {
            if (isRunning) {
                val ip = tvIp.text.toString().replace("IP: ", "")
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("ip", ip))
                Toast.makeText(this, "IP скопирован!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startProxy() {
        val ip = getLocalIp()
        proxyServer = ProxyServer(8080) { count -> runOnUiThread { tvConnections.text = "Подключений: $count" } }
        try {
            proxyServer!!.start()
            isRunning = true
            btnToggle.text = "Остановить"
            tvStatus.text = "✅ Прокси работает"
            tvIp.text = "IP: $ip"
            tvPort.text = "Порт: 8080"
            tvHint.text = "Введи IP и порт в настройки Wi-Fi → Прокси → Вручную на другом устройстве"
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopProxy() {
        proxyServer?.stop()
        isRunning = false
        btnToggle.text = "Запустить прокси"
        tvStatus.text = "⏸ Остановлен"
        tvIp.text = "IP: —"
        tvPort.text = "Порт: —"
        tvConnections.text = "Подключений: 0"
        tvHint.text = "Сначала включи VPN и хотспот, затем запусти прокси"
    }

    private fun getLocalIp(): String {
        return try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip != 0) {
                val b = byteArrayOf((ip and 0xFF).toByte(), (ip shr 8 and 0xFF).toByte(), (ip shr 16 and 0xFF).toByte(), (ip shr 24 and 0xFF).toByte())
                InetAddress.getByAddress(b).hostAddress ?: "10.0.0.1"
            } else "10.0.0.1"
        } catch (e: Exception) { "10.0.0.1" }
    }

    override fun onDestroy() { super.onDestroy(); proxyServer?.stop() }
}
