package dev.sohan.linkpilot.data

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DiagStatus { OK, WARN, FAIL }

data class DiagCheck(val label: String, val status: DiagStatus, val detail: String, val elapsedMs: Long)

/**
 * On-device checks only. There is no ICMP endpoint in the router API, so the app never
 * claims to "ping" — it reports HTTP, DNS and TCP results with their real latency.
 */
class Diagnostics(private val api: TendaApi) {

    suspend fun run(): List<DiagCheck> = withContext(Dispatchers.IO) {
        listOf(routerApi(), tcpRouter(), dns(), internet())
    }

    private suspend fun routerApi(): DiagCheck {
        var ok = false
        val elapsed = measureTimeMillis {
            ok = runCatching { api.get("getHomePageInfo", listOf("loginAuth")) }.isSuccess
        }
        return DiagCheck(
            "Router API",
            if (ok) DiagStatus.OK else DiagStatus.FAIL,
            if (ok) "Session valid · ${api.baseUrl}" else "No JSON reply from ${api.baseUrl}",
            elapsed,
        )
    }

    private fun tcpRouter(): DiagCheck {
        val host = runCatching { URI(api.baseUrl).host }.getOrNull() ?: return DiagCheck(
            "TCP 80", DiagStatus.FAIL, "Address is not a URL", 0,
        )
        var connected = false
        val elapsed = measureTimeMillis {
            connected = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, 80), 3000)
                    true
                }
            }.getOrDefault(false)
        }
        return DiagCheck(
            "TCP 80",
            if (connected) DiagStatus.OK else DiagStatus.FAIL,
            if (connected) "$host:80 accepted a connection" else "Connection refused or timed out",
            elapsed,
        )
    }

    private fun dns(): DiagCheck {
        var addresses: List<String> = emptyList()
        val elapsed = measureTimeMillis {
            addresses = runCatching { InetAddress.getAllByName("example.com").map { it.hostAddress ?: "" } }
                .getOrDefault(emptyList())
        }
        return if (addresses.isEmpty()) {
            DiagCheck("DNS", DiagStatus.FAIL, "example.com did not resolve", elapsed)
        } else {
            DiagCheck("DNS", DiagStatus.OK, addresses.take(2).joinToString(", "), elapsed)
        }
    }

    private fun internet(): DiagCheck {
        var code = -1
        val elapsed = measureTimeMillis {
            runCatching {
                val connection = URI("https://connectivitycheck.gstatic.com/generate_204")
                    .toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                code = connection.responseCode
                connection.disconnect()
            }
        }
        return when (code) {
            204, 200 -> DiagCheck("Internet", DiagStatus.OK, "Reached the captive-portal probe", elapsed)
            -1 -> DiagCheck("Internet", DiagStatus.FAIL, "No reply (captive portal or no uplink)", elapsed)
            else -> DiagCheck("Internet", DiagStatus.WARN, "Probe answered HTTP $code", elapsed)
        }
    }
}
