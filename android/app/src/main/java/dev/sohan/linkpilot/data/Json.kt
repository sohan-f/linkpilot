package dev.sohan.linkpilot.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The firmware answers almost everything as strings, including numbers and flags, and
 * renames a few keys (see docs/TENDA_WEB_API.md §8). These helpers keep that noise in one place.
 */
internal fun JSONObject.s(key: String, default: String = ""): String =
    optString(key, default).let { if (it == "null" || it == "undefined") default else it }

internal fun JSONObject.b(key: String): Boolean = s(key).equals("true", ignoreCase = true)

internal fun JSONObject.i(key: String, default: Int = 0): Int =
    s(key).toIntOrNull() ?: default

internal fun JSONObject.list(key: String): List<JSONObject> {
    val arr: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
}

internal fun JSONObject.module(name: String): JSONObject = optJSONObject(name) ?: JSONObject()

internal fun JSONObject.firstModule(vararg names: String): JSONObject =
    names.firstNotNullOfOrNull { optJSONObject(it) } ?: JSONObject()

/** `hostname\tremark\tmac\tup\tdown\ttrue|false` rows, one per line. */
internal fun encodeRows(rows: List<List<String>>): String =
    rows.joinToString("\n") { it.joinToString("\t") }

internal fun parseSemicolonRules(blob: String): List<List<String>> =
    blob.split('~').filter { it.isNotBlank() }.map { it.split(';') }

internal fun encodeSemicolonRules(rows: List<List<String>>): String =
    rows.joinToString("~") { it.joinToString(";") }
