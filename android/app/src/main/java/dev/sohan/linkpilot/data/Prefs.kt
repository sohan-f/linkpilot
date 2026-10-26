package dev.sohan.linkpilot.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SeenDevice(val firstSeen: Long, val lastSeen: Long, val label: String)

data class AuditEntry(
    val id: String,
    val at: Long,
    val operationId: String,
    val title: String,
    val detail: String,
    val outcome: String,
)

/** Stores app-only preferences, device history and audit entries on this phone. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("linkpilot", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString("base_url", DEFAULT_BASE) ?: DEFAULT_BASE
        set(value) = sp.edit { putString("base_url", normalizeBase(value)) }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) = sp.edit { putString("theme", value.name) }

    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic_color", true)
        set(value) = sp.edit { putBoolean("dynamic_color", value) }

    var rememberPassword: Boolean
        get() = sp.getBoolean("remember_password", true)
        set(value) = sp.edit { putBoolean("remember_password", value) }

    var lastSsid: String
        get() = sp.getString("last_ssid", "").orEmpty()
        set(value) = sp.edit { putString("last_ssid", value) }

    @Synchronized
    fun alias(mac: String): String = aliases().optString(mac.lowercase(), "")

    @Synchronized
    fun setAlias(mac: String, name: String) {
        val json = aliases()
        if (name.isBlank()) json.remove(mac.lowercase()) else json.put(mac.lowercase(), name.trim())
        sp.edit { putString("aliases", json.toString()) }
    }

    fun aliasMap(): Map<String, String> {
        val json = aliases()
        return buildMap {
            json.keys().forEach { key ->
                val value = json.optString(key)
                if (value.isNotBlank()) put(key, value)
            }
        }
    }

    private fun aliases(): JSONObject =
        runCatching { JSONObject(sp.getString("aliases", "{}")!!) }.getOrDefault(JSONObject())

    fun favorites(): Set<String> =
        sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

    @Synchronized
    fun toggleFavorite(mac: String): Boolean {
        val key = mac.lowercase()
        val next = favorites().toMutableSet()
        val added = if (key in next) false else true
        if (added) next.add(key) else next.remove(key)
        sp.edit { putStringSet("favorites", next) }
        return added
    }

    private var seenCache: JSONObject? = null

    @Synchronized
    private fun seenJson(): JSONObject = seenCache
        ?: runCatching { JSONObject(sp.getString("seen", "{}")!!) }
            .getOrDefault(JSONObject())
            .also { seenCache = it }

    @Synchronized
    fun recordSightings(clients: List<Client>) {
        if (clients.isEmpty()) return
        val now = System.currentTimeMillis()
        val json = seenJson()
        clients.forEach { client ->
            val key = client.mac.lowercase()
            if (key.isBlank()) return@forEach
            val prior = json.optJSONObject(key)
            json.put(
                key,
                JSONObject()
                    .put("first", prior?.optLong("first", now) ?: now)
                    .put("last", now)
                    .put("label", client.label),
            )
        }
        // Keep the map bounded; oldest last-seen entries go first.
        if (json.length() > 300) {
            val entries = json.keys().asSequence().toList()
                .sortedBy { json.optJSONObject(it)?.optLong("last", 0) ?: 0 }
            entries.take(json.length() - 300).forEach { json.remove(it) }
        }
        sp.edit { putString("seen", json.toString()) }
    }

    @Synchronized
    fun seen(mac: String): SeenDevice? {
        val obj = seenJson().optJSONObject(mac.lowercase()) ?: return null
        return SeenDevice(obj.optLong("first"), obj.optLong("last"), obj.optString("label"))
    }

    @Volatile
    private var auditCache: List<AuditEntry>? = null

    fun audit(): List<AuditEntry> {
        auditCache?.let { return it }
        val raw = sp.getString("audit", "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val entries = (array.length() - 1 downTo 0).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            AuditEntry(
                id = o.optString("id"),
                at = o.optLong("at"),
                operationId = o.optString("op"),
                title = o.optString("title"),
                detail = o.optString("detail"),
                outcome = o.optString("outcome"),
            )
        }
        auditCache = entries
        return entries
    }

    @Synchronized
    fun appendAudit(operationId: String, title: String, detail: String, outcome: String) {
        val array = runCatching { JSONArray(sp.getString("audit", "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        array.put(
            JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("at", System.currentTimeMillis())
                .put("op", operationId)
                .put("title", title)
                .put("detail", detail.take(300))
                .put("outcome", outcome),
        )
        val trimmed = JSONArray()
        val start = (array.length() - 100).coerceAtLeast(0)
        for (i in start until array.length()) trimmed.put(array.get(i))
        sp.edit { putString("audit", trimmed.toString()) }
        auditCache = null
    }

    fun clearAudit() {
        sp.edit { remove("audit") }
        auditCache = emptyList()
    }
}
