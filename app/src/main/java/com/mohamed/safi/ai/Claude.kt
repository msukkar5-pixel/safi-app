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

data class Provider(
    val id: String,
    val label: String,
    val kind: String,          // anthropic | openai | gemini
    val baseUrl: String,
    val model: String,
    val fastModel: String,
    val keyUrl: String,
    val vision: Boolean = true,
    val maxParam: String = "max_tokens",
)

/** Every AI the app can talk to. Model names are defaults; the user can pick any model the provider lists. */
object Providers {
    val all = listOf(
        Provider("anthropic", "Claude (Anthropic)", "anthropic", "https://api.anthropic.com", "claude-sonnet-5-5", "claude-haiku-4-5-20251001", "platform.claude.com/settings/keys"),
        Provider("openai", "ChatGPT (OpenAI)", "openai", "https://api.openai.com/v1", "gpt-5.4-mini", "gpt-5.4-mini", "platform.openai.com/api-keys", maxParam = "max_completion_tokens"),
        Provider("gemini", "Gemini (Google)", "gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-3.6-flash", "gemini-3.5-flash-lite", "aistudio.google.com/apikey"),
        Provider("deepseek", "DeepSeek", "openai", "https://api.deepseek.com", "deepseek-chat", "deepseek-chat", "platform.deepseek.com/api_keys", vision = false),
        Provider("groq", "Groq", "openai", "https://api.groq.com/openai/v1", "", "", "console.groq.com/keys"),
        Provider("openrouter", "OpenRouter (أي موديل)", "openai", "https://openrouter.ai/api/v1", "openai/gpt-5.4-mini", "openai/gpt-5.4-mini", "openrouter.ai/keys"),
        Provider("custom", "سيرفر تاني متوافق مع OpenAI", "openai", "", "", "", ""),
    )

    fun get(id: String) = all.firstOrNull { it.id == id } ?: all.first()
    val current: Provider get() = get(SafiApp.prefs.aiProvider)
}

/**
 * One entry point for every AI provider. Messages use the Anthropic shape
 * ({role, content: string | [ {type:text}, {type:image, source:{base64}} ]}) and are converted per provider.
 * The name stays "Claude" so the rest of the app doesn't care which AI is behind it.
 */
object Claude {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val hasKey: Boolean get() = SafiApp.prefs.apiKey.isNotBlank() && (Providers.current.id != "custom" || SafiApp.prefs.aiBaseUrl.isNotBlank())
    val providerLabel: String get() = Providers.current.label

    private fun baseUrl(p: Provider): String =
        (if (p.id == "custom") SafiApp.prefs.aiBaseUrl else SafiApp.prefs.aiBaseUrl.ifBlank { p.baseUrl }).trimEnd('/')

    suspend fun call(
        system: String,
        messages: JSONArray,
        model: String = SafiApp.prefs.model,
        maxTokens: Int = 2048,
    ): String = withContext(Dispatchers.IO) {
        val key = SafiApp.prefs.apiKey
        val p = Providers.current
        if (key.isBlank()) throw ClaudeException("اربط ذكاء اصطناعي من الإعدادات الأول (المفتاح فاضي)")
        if (model.isBlank()) throw ClaudeException("اختار الموديل من الإعدادات")
        if (!p.vision && hasImage(messages)) throw ClaudeException("${p.label} مش بيقرا صور. اختار مزود تاني للفواتير والأكل بالصور.")
        val req = when (p.kind) {
            "anthropic" -> anthropicRequest(p, key, system, messages, model, maxTokens)
            "gemini" -> geminiRequest(p, key, system, messages, model, maxTokens)
            else -> openAiRequest(p, key, system, messages, model, maxTokens)
        }
        try {
            http.newCall(req).execute().use { r ->
                val s = r.body?.string() ?: ""
                if (!r.isSuccessful) throw ClaudeException(errorText(r.code, s, p))
                val j = JSONObject(s)
                when (p.kind) {
                    "anthropic" -> {
                        val content = j.getJSONArray("content")
                        (0 until content.length()).map { content.getJSONObject(it) }
                            .filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }
                    }
                    "gemini" -> {
                        val parts = j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                            ?: throw ClaudeException("الرد فاضي من ${p.label}")
                        (0 until parts.length()).map { parts.getJSONObject(it) }
                            .filter { !it.optBoolean("thought", false) }.joinToString("") { it.optString("text") }
                    }
                    else -> {
                        val msg = j.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                        msg.optString("content").let { if (it == "null") "" else it }
                    }
                }
            }
        } catch (e: ClaudeException) {
            throw e
        } catch (e: java.io.IOException) {
            throw ClaudeException("مفيش اتصال بالإنترنت أو الاتصال اتقطع")
        } catch (e: org.json.JSONException) {
            throw ClaudeException("رد غير مفهوم من ${p.label}")
        }
    }

    private fun hasImage(m: JSONArray): Boolean = (0 until m.length()).any { i ->
        val c = m.getJSONObject(i).opt("content")
        c is JSONArray && (0 until c.length()).any { c.getJSONObject(it).optString("type") == "image" }
    }

    private fun errorText(code: Int, body: String, p: Provider): String {
        val msg = runCatching {
            val j = JSONObject(body)
            j.optJSONObject("error")?.optString("message") ?: j.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return when (code) {
            400 -> "الطلب مرفوض من ${p.label}: ${msg ?: code}"
            401, 403 -> "المفتاح غلط أو مالوش صلاحية (${p.label})" + (msg?.let { ": $it" } ?: "")
            402 -> "الحساب محتاج رصيد (${p.label})"
            404 -> "الموديل ده مش موجود عند ${p.label}. اختار موديل من القايمة في الإعدادات."
            429 -> "طلبات كتير أو الرصيد خلص، جرب كمان شوية"
            500, 502, 503, 529 -> "${p.label} مشغول حاليًا، جرب كمان شوية"
            else -> "خطأ $code: ${msg ?: body.take(150)}"
        }
    }

    private val JSON = "application/json".toMediaType()

    private fun anthropicRequest(p: Provider, key: String, system: String, messages: JSONArray, model: String, maxTokens: Int): Request {
        val body = JSONObject().put("model", model).put("max_tokens", maxTokens).put("system", system).put("messages", messages)
        return Request.Builder().url(baseUrl(p) + "/v1/messages")
            .addHeader("x-api-key", key).addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON)).build()
    }

    private fun openAiRequest(p: Provider, key: String, system: String, messages: JSONArray, model: String, maxTokens: Int): Request {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val c = m.opt("content")
            val content: Any = if (c is JSONArray) {
                val arr = JSONArray()
                for (k in 0 until c.length()) {
                    val b = c.getJSONObject(k)
                    when (b.optString("type")) {
                        "text" -> arr.put(JSONObject().put("type", "text").put("text", b.optString("text")))
                        "image" -> {
                            val src = b.getJSONObject("source")
                            arr.put(
                                JSONObject().put("type", "image_url").put(
                                    "image_url", JSONObject().put("url", "data:${src.optString("media_type", "image/jpeg")};base64,${src.optString("data")}"),
                                ),
                            )
                        }
                    }
                }
                arr
            } else c.toString()
            msgs.put(JSONObject().put("role", m.optString("role")).put("content", content))
        }
        val body = JSONObject().put("model", model).put("messages", msgs)
            .put(if (p.id == "openai") "max_completion_tokens" else p.maxParam, if (p.id == "openai") maxTokens + 4000 else maxTokens)
        val b = Request.Builder().url(baseUrl(p) + "/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(JSON))
        if (p.id == "openrouter") b.addHeader("X-Title", SafiApp.prefs.appName)
        return b.build()
    }

    private fun geminiRequest(p: Provider, key: String, system: String, messages: JSONArray, model: String, maxTokens: Int): Request {
        val contents = JSONArray()
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val parts = JSONArray()
            val c = m.opt("content")
            if (c is JSONArray) {
                for (k in 0 until c.length()) {
                    val b = c.getJSONObject(k)
                    when (b.optString("type")) {
                        "text" -> parts.put(JSONObject().put("text", b.optString("text")))
                        "image" -> {
                            val src = b.getJSONObject("source")
                            parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", src.optString("media_type", "image/jpeg")).put("data", src.optString("data"))))
                        }
                    }
                }
            } else parts.put(JSONObject().put("text", c.toString()))
            contents.put(JSONObject().put("role", if (m.optString("role") == "assistant") "model" else "user").put("parts", parts))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens + 8000))
        return Request.Builder().url(baseUrl(p) + "/models/" + model + ":generateContent")
            .addHeader("x-goog-api-key", key)
            .post(body.toString().toRequestBody(JSON)).build()
    }

    /** Models the provider offers for this key (for the picker in Settings). */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val p = Providers.current
        val key = SafiApp.prefs.apiKey
        if (key.isBlank()) throw ClaudeException("حط المفتاح الأول")
        val req = when (p.kind) {
            "anthropic" -> Request.Builder().url(baseUrl(p) + "/v1/models?limit=100").addHeader("x-api-key", key).addHeader("anthropic-version", "2023-06-01").build()
            "gemini" -> Request.Builder().url(baseUrl(p) + "/models?pageSize=200").addHeader("x-goog-api-key", key).build()
            else -> Request.Builder().url(baseUrl(p) + "/models").addHeader("Authorization", "Bearer $key").build()
        }
        try {
            http.newCall(req).execute().use { r ->
                val s = r.body?.string() ?: ""
                if (!r.isSuccessful) throw ClaudeException(errorText(r.code, s, p))
                val j = JSONObject(s)
                val arr = j.optJSONArray("data") ?: j.optJSONArray("models") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    if (p.kind == "gemini") {
                        val methods = o.optJSONArray("supportedGenerationMethods")
                        val ok = methods == null || (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                        if (ok) o.optString("name").removePrefix("models/") else null
                    } else o.optString("id").takeIf { it.isNotBlank() }
                }.sorted()
            }
        } catch (e: java.io.IOException) {
            throw ClaudeException("مفيش اتصال بالإنترنت")
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
