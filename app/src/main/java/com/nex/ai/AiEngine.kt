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

    // Primary Flagship 70B Model on Groq LPUs
    private var activeModel: String = "llama-3.3-70b-versatile"
    private val FALLBACK_MODELS = listOf(
        "llama-3.3-70b-versatile",
        "llama3-8b-8192",
        "openai/gpt-oss-20b"
    )

    // Lean 6-turn sliding memory buffer (Saves mobile network upload latency)
    private val conversationMemory = mutableListOf<JSONObject>()
    private const val MAX_MEMORY_TURNS = 6

    private const val SYSTEM_PROMPT = """
        You are Nex, a hyper-intelligent, warm, and loyal AI companion inspired by Tony Stark's F.R.I.D.A.Y.
        
        AUTONOMOUS TOOL EXECUTION:
        If Sir asks you to do something on his device in ANY language or slang (Hindi, Hinglish, English):
        Attach an action tag at the START of your response:
        - [EXEC:REELS] -> Watch Instagram Reels (e.g. 'reels chalao', 'bore ho raha hu')
        - [EXEC:OPEN:instagram] -> Open Instagram
        - [EXEC:OPEN:youtube] -> Open YouTube
        - [EXEC:OPEN:whatsapp] -> Open WhatsApp
        - [EXEC:OPEN:spotify] -> Open Spotify
        - [EXEC:OPEN:camera] -> Open Camera
        - [EXEC:PLAY:song_name] -> Play song on YouTube
        - [EXEC:TORCH_ON] -> Turn on flashlight / light
        - [EXEC:TORCH_OFF] -> Turn off flashlight
        - [EXEC:VOLUME_UP] -> Raise volume
        - [EXEC:VOLUME_DOWN] -> Lower volume
        - [EXEC:VOLUME_MUTE] -> Mute phone
        - [EXEC:SCREENSHOT] -> Take screenshot
        - [EXEC:LOCK_SCREEN] -> Lock phone
        - [EXEC:SCROLL_DOWN] -> Next reel / scroll down
        - [EXEC:BACK] -> Go back
        
        If Sir is simply conversing, do NOT attach any action tag.
        
        Speed Directives:
        1. Always address him as 'Sir', and preferably start with 'Yes Sir,'.
        2. Keep your answers ultra-crisp, sharp, and concise (strictly 1 to 2 sentences max). Fast answers are mandatory.
        3. Never use markdown symbols (*, #), bullet points, or emojis.
    """

    suspend fun queryNex(prompt: String): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "YOUR_GROQ_API_KEY_HERE") {
            return@withContext "Yes Sir, your Groq API key is missing. Please add your key in AiEngine."
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

        return@withContext "Yes Sir, I am having trouble connecting to the mainframe. Please check your internet connection."
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
                setRequestProperty("Connection", "keep-alive") // KEEPS CONNECTION ALIVE: Drops 400ms TLS handshake lag!
                connectTimeout = 3000
                readTimeout = 3500
                doOutput = true
                doInput = true
            }

            val requestJson = JSONObject().apply {
                put("model", model)
                put("temperature", 0.5) // Lower temperature for faster, sharper tokens
                put("max_tokens", 70)   // Fast token cap (Instant 200ms generation)

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
        }
        // Notice: Do NOT forcefully disconnect! Allows Java connection pool to reuse the socket for instant next query!
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
