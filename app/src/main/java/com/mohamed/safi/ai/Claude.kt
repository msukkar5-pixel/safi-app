package com.mohamed.safi.ai

import com.mohamed.safi.SafiApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClaudeException(msg: String) : Exception(msg)

object Claude {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val hasKey: Boolean get() = SafiApp.prefs.apiKey.isNotBlank()

    suspend fun call(
        system: String,
        messages: JSONArray,
        model: String = SafiApp.prefs.model,
        maxTokens: Int = 2048,
    ): String = withContext(Dispatchers.IO) {
        val key = SafiApp.prefs.apiKey
        if (key.isBlank()) throw ClaudeException("ضيف مفتاح Claude API من الإعدادات الأول")
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", messages)
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            http.newCall(req).execute().use { r ->
                val s = r.body?.string() ?: ""
                if (!r.isSuccessful) {
                    val msg = runCatching { JSONObject(s).getJSONObject("error").getString("message") }.getOrNull()
                    throw ClaudeException(
                        when (r.code) {
                            401 -> "مفتاح الـ API غلط، راجعه من الإعدادات"
                            402, 403 -> "الحساب محتاج رصيد أو صلاحية: ${msg ?: r.code}"
                            429 -> "طلبات كتير ورا بعض، جرب كمان دقيقة"
                            529, 503 -> "Claude مشغول حاليًا، جرب كمان شوية"
                            else -> "خطأ ${r.code}: ${msg ?: s.take(150)}"
                        },
                    )
                }
                val content = JSONObject(s).getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val c = content.getJSONObject(i)
                    if (c.optString("type") == "text") sb.append(c.optString("text"))
                }
                sb.toString()
            }
        } catch (e: ClaudeException) {
            throw e
        } catch (e: java.io.IOException) {
            throw ClaudeException("مفيش اتصال بالإنترنت أو الاتصال اتقطع")
        }
    }

    fun userText(text: String): JSONObject = JSONObject().put("role", "user").put("content", text)

    fun userImage(base64Jpeg: String, text: String): JSONObject {
        val content = JSONArray()
            .put(
                JSONObject().put("type", "image").put(
                    "source",
                    JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", base64Jpeg),
                ),
            )
            .put(JSONObject().put("type", "text").put("text", text))
        return JSONObject().put("role", "user").put("content", content)
    }

    fun extractJson(s: String): JSONObject? {
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return runCatching { JSONObject(s.substring(a, b + 1)) }.getOrNull()
    }
}
