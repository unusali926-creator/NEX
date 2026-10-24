package com.nex.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit,
    private val onStateUpdate: (String) -> Unit
) {

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    var isSpeaking: Boolean = false
        private set

    init {
        initTTS()
    }

    private fun initTTS() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Calm, British / FRIDAY cadence
                val result = textToSpeech?.setLanguage(Locale.UK)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.language = Locale.US
                }
                textToSpeech?.setPitch(0.95f) // Calm, sophisticated pitch
                textToSpeech?.setSpeechRate(0.95f) // Controlled, clear cadence

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        onStateUpdate("SPEAKING")
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        onStateUpdate("IDLE")
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        onStateUpdate("IDLE")
                    }
                })
            }
        }
    }

    fun startListening() {
        if (isSpeaking) {
            // Echo Shield: Mic tab tak nahi khulega jab tak Nex bol raha ho
            return
        }

        mainHandler.post {
            try {
                destroyRecognizer()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            onStateUpdate("LISTENING")
                        }

                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            onStateUpdate("PROCESSING")
                        }

                        override fun onError(error: Int) {
                            onStateUpdate("IDLE")
                            // Error Transparency: Chup na baithkar bolkar bataye
                            when (error) {
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                                    speak("Sir, connection interrupted. Network check karein.")
                                }
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                    speak("Sir, aawaz saaf nahi aayi. Kripya dobara bolein.")
                                }
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val query = matches[0]
                                onSpeechRecognized(query)
                            } else {
                                onStateUpdate("IDLE")
                                speak("Sir, aawaz decode nahi ho payi.")
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                onStateUpdate("IDLE")
                speak("Sir, audio interface me rukawat aayi hai.")
            }
        }
    }

    fun speak(text: String) {
        mainHandler.post {
            // Bolne se pehle mic ko mute/stop karein
            stopListening()
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "NEX_SPEECH_ID")
            }
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "NEX_SPEECH_ID")
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
    }

    fun shutdown() {
        stopListening()
        destroyRecognizer()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
