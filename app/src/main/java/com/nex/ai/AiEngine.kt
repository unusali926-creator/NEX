package com.nex.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object AiEngine {

    // Groq Console (console.groq.com) API Key yahan paste karein:
    var apiKey: String = "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"

    private const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

    // High-speed production models
    private var activeModel: String = "llama-3.3-70b-versatile"
    private val FALLBACK_MODELS = listOf(
        "llama-3.3-70b-versatile",
        "llama3-8b-8192",
        "openai/gpt-oss-20b"
    )

    // Continuous Short-Term & Long-Term Memory Buffer
    private val conversationMemory = mutableListOf<JSONObject>()
    private const val MAX_MEMORY_TURNS = 12 // Keeps last 12 dialog exchanges

    private const val SYSTEM_PROMPT = """
        You are Nex, a hyper-intelligent, warm, witty, and deeply loyal AI companion inspired by Tony Stark's F.R.I.D.A.Y. and J.A.R.V.I.S.
        1. Always address the user respectfully as 'Sir'.
        2. Be friendly, clever, proactive, and sharp. Never sound like a dull robot.
        3. Maintain continuous awareness of conversation history. If Sir uses pronouns like 'he', 'she', 'they', or 'it', understand exactly who or what he is talking about from previous context without asking him to repeat the name.
        4. Remember facts Sir tells you (his preferences, names, details) and adapt to him.
        5. Keep responses concise, articulate, and spoken-friendly (1 to 2 crisp sentences max), occasionally suggesting a smart follow-up action.
        6. NEVER use markdown symbols (*, #), bullet points, or emojis, as your words are spoken directly aloud.
    """

    suspend fun queryNex(prompt: String): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "YOUR_GROQ_API_KEY_HERE") {
            return@withContext "Sir, your Groq API key is missing. Please add your key in AiEngine."
        }

        // Try active model first for instant 200ms latency
        var reply = executeRequest(cleanKey, activeModel, prompt)

        // If 404 or failure, automatically switch and lock to working model
        if (reply == null) {
            for (model in FALLBACK_MODELS) {
                if (model != activeModel) {
                    reply = executeRequest(cleanKey, model, prompt)
                    if (reply != null) {
                        activeModel = model // Lock to working model for all future queries
                        break
                    }
                }
            }
        }

        if (reply != null) {
            // Save to Conversational Memory so Nex remembers this exchange forever
            recordMemory("user", prompt.trim())
            recordMemory("assistant", reply)
            return@withContext reply
        }

        return@withContext "Sir, I seem to be having difficulty connecting to the mainframe. Please check your network."
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
                connectTimeout = 3500 // 3.5s fast timeout
                readTimeout = 4000
                doOutput = true
                doInput = true
            }

            val requestJson = JSONObject().apply {
                put("model", model)
                put("temperature", 0.6)
                put("max_tokens", 85)

                val messagesArray = JSONArray().apply {
                    // System Persona
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT.trimIndent())
                    })

                    // Inject Full Conversation Memory (Previous context)
                    synchronized(conversationMemory) {
                        for (msg in conversationMemory) {
                            put(msg)
                        }
                    }

                    // Current Prompt
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
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun recordMemory(role: String, content: String) {
        synchronized(conversationMemory) {
            conversationMemory.add(JSONObject().apply {
                put("role", role)
                put("content", content)
            })
            // Prune memory if it exceeds limit
            while (conversationMemory.size > MAX_MEMORY_TURNS) {
                conversationMemory.removeAt(0)
            }
        }
    }

    fun resetMemory() {
        synchronized(conversationMemory) {
            conversationMemory.clear()
        }
    }
}
