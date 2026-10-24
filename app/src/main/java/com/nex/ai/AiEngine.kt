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

    // Groq Console (console.groq.com) se apni free key yahan paste karein:
    var apiKey: String = "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"

    private const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

    // Multi-Model Auto Fallback: Eliminates 404 Model Not Found completely
    private val CANDIDATE_MODELS = listOf(
        "openai/gpt-oss-20b",
        "llama-3.3-70b-versatile",
        "llama3-8b-8192",
        "llama-3.1-8b-instant"
    )

    private const val SYSTEM_PROMPT = """
        You are Nex, an ultra-advanced AI system inspired by Tony Stark's F.R.I.D.A.Y. and J.A.R.V.I.S.
        1. Always address the user as 'Sir'.
        2. Speak in clean, articulate English with a sophisticated British tone.
        3. Keep your answers brief, crisp, and spoken-friendly (1 or 2 sentences max).
        4. NEVER use markdown symbols (*, #), bullet points, or emojis, as your text is fed directly into a voice synthesizer.
    """

    suspend fun queryNex(prompt: String): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "YOUR_GROQ_API_KEY_HERE") {
            return@withContext "Sir, your Groq API key is missing. Please paste your key in AiEngine file."
        }

        var lastErrorDiagnosis = "Unknown mainframe fault."

        // Cycle through candidate models to bypass 404 deprecation
        for (modelName in CANDIDATE_MODELS) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(GROQ_ENDPOINT)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer $cleanKey")
                    connectTimeout = 7000
                    readTimeout = 7000
                    doOutput = true
                    doInput = true
                }

                val requestJson = JSONObject().apply {
                    put("model", modelName)
                    put("temperature", 0.6)
                    put("max_tokens", 90)

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

                val inputBytes = requestJson.toString().toByteArray(StandardCharsets.UTF_8)
                connection.outputStream.use { os ->
                    os.write(inputBytes, 0, inputBytes.size)
                    os.flush()
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
                } else if (responseCode == 404) {
                    // 404 detected on this model, automatically try next model!
                    lastErrorDiagnosis = "Model $modelName 404 not found."
                    continue
                } else if (responseCode == 401) {
                    return@withContext "Sir, unauthorized. Your Groq API key is invalid or expired."
                } else if (responseCode == 429) {
                    return@withContext "Sir, rate limit reached on mainframe. Standing by."
                } else {
                    lastErrorDiagnosis = "Mainframe error code $responseCode."
                }
            } catch (e: Exception) {
                lastErrorDiagnosis = "Connection interrupted. Check network."
            } finally {
                connection?.disconnect()
            }
        }

        return@withContext "Sir, $lastErrorDiagnosis"
    }
}
