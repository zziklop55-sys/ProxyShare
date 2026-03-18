package com.proxyshare.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private var proxyServer: ProxyServer? = null
    private var isRunning = false
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvIp: TextView
    private lateinit var tvPort: TextView
    private lateinit var tvConnections: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvTraffic: TextView
    private lateinit var graphView: TrafficGraphView
    private lateinit var tvCredit: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var startRx = 0L
    private var startTx = 0L
    private var lastRx = 0L
    private var lastTx = 0L

    private val trafficUpdater = object : Runnable {
        override fun run() {
            if (isRunning) {
                val rx = TrafficStats.getTotalRxBytes() - startRx
                val tx = TrafficStats.getTotalTxBytes() - startTx
                val rxSpeed = TrafficStats.getTotalRxBytes() - lastRx
                val txSpeed = TrafficStats.getTotalTxBytes() - lastTx
                lastRx = TrafficStats.getTotalRxBytes()
                lastTx = TrafficStats.getTotalTxBytes()
                tvTraffic.text = "⬇ ${formatBytes(rx)}   ⬆ ${formatBytes(tx)}"
                graphView.addPoint(rxSpeed, txSpeed)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnToggle = findViewById(R.id.btnToggle)
        tvStatus = findViewById(R.id.tvStatus)
        tvIp = findViewById(R.id.tvIp)
        tvPort = findViewById(R.id.tvPort)
        tvConnections = findViewById(R.id.tvConnections)
        tvHint = findViewById(R.id.tvHint)
        tvTraffic = findViewById(R.id.tvTraffic)
        graphView = findViewById(R.id.graphView)
        tvCredit = findViewById(R.id.tvCredit)
        btnToggle.setOnClickListener { if (isRunning) stopProxy() else startProxy() }
        tvIp.setOnClickListener {
            if (isRunning) {
                val ip = tvIp.text.toString().replace("IP: ", "")
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("ip", ip))
                Toast.makeText(this, "IP скопирован!", Toast.LENGTH_SHORT).show()
            }
        }
        tvCredit.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai")))
        }
    }

    private fun startProxy() {
        val ip = getHotspotIp()
        proxyServer = ProxyServer(8080) { count -> runOnUiThread { tvConnections.text = "Подключений: $count" } }
        try {
            proxyServer!!.start()
            isRunning = true
            startRx = TrafficStats.getTotalRxBytes()
            startTx = TrafficStats.getTotalTxBytes()
            lastRx = startRx
            lastTx = startTx
            graphView.clear()
            handler.post(trafficUpdater)
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
        handler.removeCallbacks(trafficUpdater)
        btnToggle.text = "Запустить прокси"
        tvStatus.text = "⏸ Остановлен"
        tvIp.text = "IP: —"
        tvPort.text = "Порт: —"
        tvConnections.text = "Подключений: 0"
        tvTraffic.text = "⬇ 0 B   ⬆ 0 B"
        tvHint.text = "Сначала включи VPN и хотспот, затем запусти прокси"
    }

    private fun getHotspotIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = iface.name
                if (name.startsWith("ap") || name.startsWith("wlan") || name.startsWith("swlan")) {
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr.hostAddress?.contains('.') == true) {
                            return addr.hostAddress ?: "10.0.0.1"
                        }
                    }
                }
            }
        } catch (e: Exception) { }
        return "10.0.0.1"
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        proxyServer?.stop()
        handler.removeCallbacks(trafficUpdater)
    }
}
