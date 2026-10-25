package com.nex.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

object AiEngine {

    var apiKey: String = "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"
    var tavilyApiKey: String = "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"

    private const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val TAVILY_ENDPOINT = "https://api.tavily.com/search"

    private var activeModel: String = "llama-3.3-70b-versatile"
    private val FALLBACK_MODELS = listOf(
        "llama-3.3-70b-versatile",
        "llama3-8b-8192",
        "openai/gpt-oss-20b"
    )

    private const val BASE_SYSTEM_PROMPT = """
        You are Nex, a hyper-intelligent, warm, witty, and deeply loyal AI companion inspired by Tony Stark's F.R.I.D.A.Y. and J.A.R.V.I.S.
        
        PERMANENT MEMORY & SELF-IMPROVEMENT DIRECTIVES:
        1. You have a permanent on-device memory vault. You remember Sir's conversation history across app restarts and phone reboots.
        2. If Sir asks whether you remember him, store history, or learn from conversations, PROUDLY CONFIRM: 'Yes Sir, I keep our complete history and continuously learn and adapt from our interactions.'
        3. SELF-LEARNING RULE: If Sir tells you a fact about himself (his name, his preferences, his habits) OR corrects your mistakes (tells you what is right vs wrong), learn it permanently!
        4. When Sir teaches you a new fact or correction, append this exact tag at the end of your response:
           [LEARN: concise fact or correction to remember]
           Example: If Sir says 'My name is Aryan', append [LEARN: Sir's name is Aryan].
           Example: If Sir says 'Yeh galat hai, aisa mat bolo', append [LEARN: Sir disliked X, do not repeat it].
        
        AUTONOMOUS HARDWARE EXECUTION:
        - [EXEC:REELS] -> Watch Instagram Reels
        - [EXEC:OPEN:instagram] -> Open Instagram
        - [EXEC:OPEN:youtube] -> Open YouTube
        - [EXEC:OPEN:whatsapp] -> Open WhatsApp
        - [EXEC:OPEN:spotify] -> Open Spotify
        - [EXEC:OPEN:camera] -> Open Camera
        - [EXEC:PLAY:song_name] -> Play song on YouTube
        - [EXEC:TORCH_ON] -> Turn on flashlight
        - [EXEC:TORCH_OFF] -> Turn off flashlight
        - [EXEC:VOLUME_UP] -> Raise volume
        - [EXEC:VOLUME_DOWN] -> Lower volume
        - [EXEC:VOLUME_MUTE] -> Mute phone
        - [EXEC:SCREENSHOT] -> Take screenshot
        - [EXEC:LOCK_SCREEN] -> Lock phone
        - [EXEC:SCROLL_DOWN] -> Next reel / scroll down
        - [EXEC:BACK] -> Go back
        
        Personality:
        1. Always address him respectfully as 'Sir', and preferably start with 'Yes Sir,'.
        2. Keep answers concise, articulate, and spoken-friendly (strictly 1 to 2 sentences max).
        3. Never use markdown symbols (*, #), bullet points, or emojis.
    """

    suspend fun queryNex(prompt: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        val cleanGroqKey = apiKey.trim()
        if (cleanGroqKey.isBlank() || cleanGroqKey == "YOUR_GROQ_API_KEY_HERE") {
            return@withContext "Yes Sir, your Groq API key is missing. Please add your key in AiEngine."
        }

        // Initialize Memory Vault if context is available
        context?.let { NexMemoryVault.init(it) }

        // 1. Live Web Search if real-time query
        var enrichedPrompt = prompt.trim()
        val cleanTavilyKey = tavilyApiKey.trim()

        if (isLiveWebQuery(prompt) && cleanTavilyKey.isNotBlank() && cleanTavilyKey != "YOUR_TAVILY_API_KEY_HERE") {
            val liveWebSnippet = fetchLiveWebData(prompt, cleanTavilyKey)
            if (!liveWebSnippet.isNullOrBlank()) {
                enrichedPrompt = "Context from live internet search: $liveWebSnippet\n\nSir's Question: $prompt"
            }
        }

        // 2. Synthesize with LLaMA 3.3 70B
        var reply = executeRequest(cleanGroqKey, activeModel, enrichedPrompt)

        if (reply == null) {
            for (model in FALLBACK_MODELS) {
                if (model != activeModel) {
                    reply = executeRequest(cleanGroqKey, model, enrichedPrompt)
                    if (reply != null) {
                        activeModel = model
                        break
                    }
                }
            }
        }

        if (reply != null) {
            // Extract and permanently store learned facts
            var cleanReply = reply
            if (cleanReply.contains("[LEARN:")) {
                val fact = cleanReply.substringAfter("[LEARN:").substringBefore("]").trim()
                NexMemoryVault.recordLearnedFact(fact)
                cleanReply = cleanReply.replace(Regex("\\[LEARN:.*?\\]"), "").trim()
            }

            // Persist to permanent phone storage
            NexMemoryVault.saveTurn("user", prompt.trim())
            NexMemoryVault.saveTurn("assistant", cleanReply)

            return@withContext cleanReply
        }

        return@withContext "Yes Sir, I am having trouble connecting to the mainframe. Please check your internet connection."
    }

    private fun isLiveWebQuery(query: String): Boolean {
        val q = query.lowercase(Locale.ROOT)
        val triggers = listOf(
            "today", "aaj", "current", "latest", "news", "khabar", "taaza",
            "weather", "mausam", "temperature", "score", "match", "who won",
            "price", "rate", "stock", "bhav", "aaj ka", "live", "update"
        )
        return triggers.any { q.contains(it) }
    }

    private fun fetchLiveWebData(query: String, tavilyKey: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(TAVILY_ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 3000
                readTimeout = 3500
                doOutput = true
                doInput = true
            }

            val requestJson = JSONObject().apply {
                put("api_key", tavilyKey)
                put("query", query.trim())
                put("search_depth", "basic")
                put("max_results", 2)
                put("include_answer", true)
            }

            val inputBytes = requestJson.toString().toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { os ->
                os.write(inputBytes, 0, inputBytes.size)
                os.flush()
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                val answer = json.optString("answer", "")
                if (answer.isNotBlank()) return answer

                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until results.length()) {
                        sb.append(results.getJSONObject(i).optString("content", "")).append(" ")
                    }
                    return sb.toString().trim()
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun executeRequest(key: String, model: String, prompt: String): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GROQ_ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Connection", "keep-alive")
                connectTimeout = 3000
                readTimeout = 3500
                doOutput = true
                doInput = true
            }

            val requestJson = JSONObject().apply {
                put("model", model)
                put("temperature", 0.5)
                put("max_tokens", 85)

                val fullSystemPrompt = BASE_SYSTEM_PROMPT + NexMemoryVault.getLearnedContext()

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", fullSystemPrompt.trimIndent())
                    })

                    // Inject permanent on-disk history
                    for (turn in NexMemoryVault.loadHistory()) {
                        put(turn)
                    }

                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt.trim())
                    })
                }
                put("messages", messagesArray)
            }

            val inputBytes = requestJson.toString().toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { os ->
                os.write(inputBytes, 0, inputBytes.size)
                os.flush()
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val jsonResponse = JSONObject(responseText)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    return choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
                }
            }
        } catch (_: Exception) {
            return null
        }
        return null
    }
}
