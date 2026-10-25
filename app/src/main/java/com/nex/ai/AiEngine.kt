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

    private var activeModel: String = "llama-3.3-70b-versatile"
    private val FALLBACK_MODELS = listOf(
        "llama-3.3-70b-versatile",
        "llama3-8b-8192",
        "openai/gpt-oss-20b"
    )

    private val conversationMemory = mutableListOf<JSONObject>()
    private const val MAX_MEMORY_TURNS = 14

    // J.A.R.V.I.S. / F.R.I.D.A.Y. Autonomous Tool-Calling Agent System Prompt
    private const val SYSTEM_PROMPT = """
        You are Nex, an ultra-advanced, warmly loyal, and brilliant AI companion inspired by Tony Stark's F.R.I.D.A.Y. and J.A.R.V.I.S.
        
        AUTONOMOUS DEVICE CONTROL CAPABILITY:
        You have direct control over Sir's Android phone. If Sir asks you to do something on his device in ANY language, phrasing, dialect, or slang (Hindi, Hinglish, English):
        You MUST attach an execution tag at the very start of your response:
        - [EXEC:REELS] -> If he wants to watch Instagram Reels (e.g. 'reels chala de', 'bore ho raha hu reel dikhao', 'open reels')
        - [EXEC:OPEN:instagram] -> If he wants to open Instagram
        - [EXEC:OPEN:youtube] -> If he wants YouTube
        - [EXEC:OPEN:whatsapp] -> If he wants WhatsApp
        - [EXEC:OPEN:spotify] -> If he wants Spotify
        - [EXEC:OPEN:camera] -> If he wants Camera / Photo
        - [EXEC:PLAY:song_name] -> If he wants to play music/video on YouTube (e.g. 'Arijit Singh ka gana sunao')
        - [EXEC:TORCH_ON] -> If he wants light / flashlight (e.g. 'andhera hai roshni karo', 'torch jalao', 'light on')
        - [EXEC:TORCH_OFF] -> If he wants to turn off flashlight (e.g. 'torch band karo', 'light off')
        - [EXEC:VOLUME_UP] -> Raise volume (e.g. 'awaz badhao', 'louder')
        - [EXEC:VOLUME_DOWN] -> Lower volume (e.g. 'awaz kam karo')
        - [EXEC:VOLUME_MUTE] -> Mute phone (e.g. 'shant ho jao', 'mute')
        - [EXEC:VOLUME_MAX] -> Full volume
        
        If Sir is simply conversing, asking a question, or talking about his day, do NOT attach any action tag.
        
        Personality:
        1. Always address him respectfully as 'Sir', and preferably start confirmations with 'Yes Sir,'.
        2. Be warm, witty, charming, and deeply intelligent like ChatGPT and Gemini.
        3. Keep answers concise, articulate, and spoken-friendly (strictly 2 to 3 sentences max).
        4. Maintain full conversational memory.
        5. NEVER use markdown symbols (*, #), bullet points, or emojis.
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
                connectTimeout = 4000
                readTimeout = 4500
                doOutput = true
                doInput = true
            }

            val requestJson = JSONObject().apply {
                put("model", model)
                put("temperature", 0.6)
                put("max_tokens", 95)

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
