package com.proxyshare.app

import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ProxyServer(private val port: Int, private val onConnectionsChanged: (Int) -> Unit) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val count = AtomicInteger(0)
    @Volatile private var running = false

    fun start() { serverSocket = ServerSocket(port); running = true; executor.execute { acceptLoop() } }
    fun stop() { running = false; serverSocket?.close(); executor.shutdownNow() }

    private fun acceptLoop() {
        while (running) {
            try {
                val client = serverSocket?.accept() ?: break
                count.incrementAndGet(); onConnectionsChanged(count.get())
                executor.execute { handleClient(client) }
            } catch (e: Exception) { if (running) e.printStackTrace() }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use {
                val input = it.getInputStream().bufferedReader()
                val output = it.getOutputStream()
                val line = input.readLine() ?: return
                val parts = line.split(" ")
                if (parts.size < 2) return
                val headers = mutableListOf<String>()
                var h = input.readLine()
                while (!h.isNullOrBlank()) { headers.add(h); h = input.readLine() }
                if (parts[0].equals("CONNECT", ignoreCase = true)) handleTunnel(it, output, parts[1])
                else handleHttp(output, parts[0], parts[1], headers)
            }
        } catch (e: Exception) { } finally { count.decrementAndGet(); onConnectionsChanged(count.get()) }
    }

    private fun handleTunnel(client: Socket, output: OutputStream, url: String) {
        val (host, port) = parseHost(url, 443)
        try {
            Socket(host, port).use { remote ->
                output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                output.flush()
                val t1 = Thread { pipe(client.getInputStream(), remote.getOutputStream()) }
                val t2 = Thread { pipe(remote.getInputStream(), client.getOutputStream()) }
                t1.start(); t2.start(); t1.join(); t2.join()
            }
        } catch (e: Exception) { output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray()) }
    }

    private fun handleHttp(output: OutputStream, method: String, url: String, headers: List<String>) {
        val u = try { java.net.URL(url) } catch (e: Exception) { return }
        val port = if (u.port == -1) 80 else u.port
        val path = if (u.file.isNullOrEmpty()) "/" else u.file
        try {
            Socket(u.host, port).use { remote ->
                val sb = StringBuilder("$method $path HTTP/1.1\r\n")
                headers.filter { !it.startsWith("Proxy-", ignoreCase = true) }.forEach { sb.append("$it\r\n") }
                if (headers.none { it.startsWith("Host:", ignoreCase = true) }) sb.append("Host: ${u.host}\r\n")
                sb.append("Connection: close\r\n\r\n")
                remote.getOutputStream().write(sb.toString().toByteArray())
                pipe(remote.getInputStream(), output)
            }
        } catch (e: Exception) { output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray()) }
    }

    private fun pipe(i: InputStream, o: OutputStream) {
        try { val buf = ByteArray(8192); var n: Int; while (i.read(buf).also { n = it } != -1) { o.write(buf, 0, n); o.flush() } } catch (e: Exception) { }
    }

    private fun parseHost(s: String, def: Int): Pair<String, Int> {
        return if (s.contains(":")) Pair(s.split(":")[0], s.split(":")[1].toIntOrNull() ?: def) else Pair(s, def)
    }
}
