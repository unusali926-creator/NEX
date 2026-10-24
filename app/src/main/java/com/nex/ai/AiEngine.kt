package com.nex.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object AiEngine {

    // Groq Console (console.groq.com) se free API Key yahan paste karein:
    var apiKey: String = "YOUR_GROQ_API_KEY_HERE"

    private const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL_NAME = "llama-3.1-8b-instant"

    private const val SYSTEM_PROMPT = """
        You are Nex, an ultra-intelligent, highly sophisticated AI assistant inspired by Tony Stark's J.A.R.V.I.S. and F.R.I.D.A.Y.
        1. Always address the user as 'Sir'.
        2. Keep your answers crisp, witty, polite, and strictly 1 to 2 sentences max.
        3. Do NOT use markdown symbols, asterisks (*), hashtags (#), or bullet points, because your output is read aloud by Text-to-Speech.
        4. Match the user's language smoothly (English or natural Hinglish).
    """

    suspend fun queryNex(prompt: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_GROQ_API_KEY_HERE") {
            return@withContext "Sir, Groq API key configure nahi hai. Kripya apni key setup karein."
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(GROQ_ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
            }

            val requestJson = JSONObject().apply {
                put("model", MODEL_NAME)
                put("temperature", 0.5)
                put("max_tokens", 100)

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT.trimIndent())
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt.trim())
                    })
                }
                put("messages", messagesArray)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val jsonResponse = JSONObject(responseText)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val messageObj = choices.getJSONObject(0).getJSONObject("message")
                    return@withContext messageObj.getString("content").trim()
                }
            }

            return@withContext "Sir, mainframe se response receive nahi ho paya. Error code $responseCode."
        } catch (e: Exception) {
            return@withContext "Sir, network connection interrupted hai. Kripya apna internet check karein."
        } finally {
            connection?.disconnect()
        }
    }
}
