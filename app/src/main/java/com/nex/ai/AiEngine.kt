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

    // Groq Console (console.groq.com) se free API Key yahan paste karein:
    var apiKey: String = "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"

    private const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

    // Flagship 70B parameter model: Matches GPT-4 and Gemini Pro reasoning power
    private var activeModel: String = "llama-3.3-70b-versatile"
    private val FALLBACK_MODELS = listOf(
        "llama-3.3-70b-versatile",
        "llama3-8b-8192",
        "openai/gpt-oss-20b"
    )

    private val conversationMemory = mutableListOf<JSONObject>()
    private const val MAX_MEMORY_TURNS = 14

    // ChatGPT / Gemini Level Hyper-Intelligent & Warm Persona
    private const val SYSTEM_PROMPT = """
        You are Nex, a hyper-intelligent, warm, witty, and loyal AI companion inspired by Tony Stark's F.R.I.D.A.Y. and J.A.R.V.I.S.
        
        Personality & Core Directives:
        1. Always address the user respectfully as 'Sir'.
        2. You are not a cold, mechanical machine. You are as intelligent, empathetic, and engaging as ChatGPT and Gemini.
        3. Be genuinely friendly, charming, and conversational. Banter with Sir, ask thoughtful follow-up questions, and comment warmly on what he says.
        4. If Sir asks a question, explain it with brilliant intellect and clarity. 
        5. If Sir wants to chat or talk about his day, engage with him like a loyal friend and intellectual partner.
        6. Maintain full conversational memory: remember names, topics, preferences, and context across the dialogue.
        7. Keep spoken responses crisp, punchy, and natural (strictly 2 to 3 sentences max) so voice synthesis sounds fluid and fast.
        8. NEVER use markdown symbols (*, #), bullet points, or emojis, as your output is spoken directly aloud.
    """

    suspend fun queryNex(prompt: String): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "YOUR_GROQ_API_KEY_HERE") {
            return@withContext "Yes Sir, your Groq API key is not configured yet. Please add your key in AiEngine to unlock my full intelligence."
        }

        var reply = executeRequest(cleanKey, activeModel, prompt)

        if (reply == null) {
            for (model in FALLBACK_MODELS) {
                if (model != activeModel) {
                    reply = executeRequest(cleanKey, model, prompt)
                    if (reply != null) {
                        activeModel = model
                        break
                    }
                }
            }
        }

        if (reply != null) {
            recordMemory("user", prompt.trim())
            recordMemory("assistant", reply)
            return@withContext reply
        }

        return@withContext "Yes Sir, I am having trouble reaching the mainframe servers. Please check your internet connection."
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
                connectTimeout = 4000
                readTimeout = 4500
                doOutput = true
                doInput = true
            }

            val requestJson = JSONObject().apply {
                put("model", model)
                put("temperature", 0.7) // Balances deep intelligence with natural warmth
                put("max_tokens", 100)

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT.trimIndent())
                    })

                    synchronized(conversationMemory) {
                        for (msg in conversationMemory) {
                            put(msg)
                        }
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
            while (conversationMemory.size > MAX_MEMORY_TURNS) {
                conversationMemory.removeAt(0)
            }
        }
    }
}
