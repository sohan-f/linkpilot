package dev.sohan.linkpilot.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

const val DEFAULT_BASE = "http://192.168.0.1"

class SessionExpiredException : IOException("Session expired")
class RouterUnreachableException(message: String, cause: Throwable? = null) : IOException(message, cause)
class RouterProtocolException(message: String) : IOException(message)

class RouterRejectedException(val code: String, val form: String) :
    IOException("$form rejected with errCode $code")

fun normalizeBase(input: String): String {
    var value = input.trim().ifBlank { "192.168.0.1" }
    if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://$value"
    return value.trimEnd('/')
}

/** Cookie-based transport for the router's `goform` API. */
class TendaApi(initialBase: String = DEFAULT_BASE) {

    @Volatile
    var baseUrl: String = normalizeBase(initialBase)
        private set

    private val cookieJar = MemoryCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _authenticated = MutableStateFlow(false)
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()

    fun setBase(input: String) {
        baseUrl = normalizeBase(input)
        cookieJar.clear()
        _authenticated.value = false
    }

    fun resetSession() {
        cookieJar.clear()
        _authenticated.value = false
    }

    suspend fun login(password: String) = withContext(Dispatchers.IO) {
        resetSession()
        val token = callJson(
            Request.Builder().url(url("goform/getstok")).get().build()
        ).s("random")
        if (token.isBlank()) throw RouterProtocolException("Router did not return a login token")

        // The UI base64-encodes the UTF-8 password, then hashes with the per-login token.
        val encoded = android.util.Base64.encodeToString(
            password.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
        )
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest((encoded + token).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val body = FormBody.Builder().add("password", digest).build()
        val response = execute(
            Request.Builder().url(url("login/Auth")).post(body).build()
        )
        response.use {
            if (!cookieJar.has("ecos_pw")) {
                val location = it.header("Location").orEmpty()
                val code = location.substringAfter('?', "").takeWhile { c -> c.isDigit() }
                throw RouterRejectedException(code.ifBlank { "1" }, "login")
            }
        }
        _authenticated.value = true
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder().add("action", "loginout").build()
            execute(Request.Builder().url(url("goform/loginOut")).post(body).build()).close()
        } catch (_: IOException) {
            // Logging out locally is enough; the cookie is discarded either way.
        } finally {
            resetSession()
        }
    }

    suspend fun get(form: String, modules: List<String> = emptyList()): org.json.JSONObject =
        withContext(Dispatchers.IO) {
            val builder = url("goform/$form").newBuilder().addQueryParameter("random", caster())
            if (modules.isNotEmpty()) builder.addQueryParameter("modules", modules.joinToString(","))
            callJson(Request.Builder().url(builder.build()).get().build())
        }

    /** Returns the router's errCode, or null when the form answered without one. */
    suspend fun post(form: String, fields: List<Pair<String, String>>): String? =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply {
                fields.forEach { (k, v) -> add(k, v) }
            }.build()
            val response = execute(
                Request.Builder()
                    .url(url("goform/$form").newBuilder().addQueryParameter("random", caster()).build())
                    .post(body)
                    .build()
            )
            response.use {
                val text = it.body?.string().orEmpty()
                if (it.code == 302) {
                    _authenticated.value = false
                    throw SessionExpiredException()
                }
                if (text.isBlank()) return@withContext "0"
                runCatching { org.json.JSONObject(text).optString("errCode", "") }
                    .getOrDefault("")
                    .ifBlank { null }
            }
        }

    suspend fun download(path: String): ByteArray = withContext(Dispatchers.IO) {
        val response = execute(Request.Builder().url(url(path.removePrefix("/"))).get().build())
        response.use {
            if (it.code == 302) throw SessionExpiredException()
            if (!it.isSuccessful) throw RouterProtocolException("Download failed (${it.code})")
            it.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun url(path: String): HttpUrl =
        baseUrl.toHttpUrl().newBuilder().addPathSegments(path).build()

    private fun caster(): String = Random.nextDouble().toString()

    private fun callJson(request: Request): org.json.JSONObject {
        val response = execute(request)
        return response.use {
            if (it.code == 302) {
                _authenticated.value = false
                throw SessionExpiredException()
            }
            val text = it.body?.string().orEmpty().trim()
            if (!text.startsWith("{")) {
                // An expired session can also come back as the login page instead of a redirect.
                if (text.contains("<!DOCTYPE", ignoreCase = true) || text.contains("<html", ignoreCase = true)) {
                    _authenticated.value = false
                    throw SessionExpiredException()
                }
                throw RouterProtocolException("Unexpected reply from ${request.url.encodedPath}")
            }
            runCatching { org.json.JSONObject(text) }.getOrElse {
                throw RouterProtocolException("Malformed JSON from ${request.url.encodedPath}")
            }
        }
    }

    private fun execute(request: Request): okhttp3.Response =
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code != 302) {
                response.close()
                throw RouterProtocolException("HTTP ${response.code} from ${request.url.encodedPath}")
            }
            response
        } catch (e: RouterProtocolException) {
            throw e
        } catch (e: IOException) {
            throw RouterUnreachableException(describeUnreachable(e, request.url.host), e)
        }
}

/** Replaces low-level network errors with messages users can act on. */
internal fun describeUnreachable(error: Throwable, host: String): String = when (error) {
    is SocketTimeoutException ->
        "The router at $host did not answer in time. Check Wi-Fi and try again."
    is ConnectException ->
        "Could not connect to the router at $host. Check Wi-Fi and the router address."
    is UnknownHostException ->
        "Could not resolve $host. Check the router address."
    else ->
        "Could not reach the router at $host. Check Wi-Fi and the router address."
}

private class MemoryCookieJar : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
        list.forEach { cookies[it.name] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.toList()

    @Synchronized
    fun has(name: String): Boolean = cookies[name]?.value?.isNotBlank() == true

    @Synchronized
    fun clear() = cookies.clear()
}
