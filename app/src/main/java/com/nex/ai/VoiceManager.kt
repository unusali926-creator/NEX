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
    private val onStateUpdate: (state: String, detail: String) -> Unit
) {

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isTtsReady: Boolean = false
    private var pendingSpeech: String? = null

    var isSpeaking: Boolean = false
        private set
    var isContinuousMode: Boolean = false
        private set

    init {
        initTTS()
    }

    private fun initTTS() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                textToSpeech?.language = Locale.UK

                // Select female voice if available
                textToSpeech?.voices?.firstOrNull { voice ->
                    voice.locale.language == "en" &&
                    (voice.name.contains("female", ignoreCase = true) || voice.name.contains("en-gb", ignoreCase = true))
                }?.let { textToSpeech?.voice = it }

                textToSpeech?.setPitch(1.05f)
                textToSpeech?.setSpeechRate(0.98f)

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        onStateUpdate("SPEAKING", "Nex is responding...")
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        onStateUpdate("IDLE", "Online and standing by, Sir.")
                        // CADENCE: Restart listening immediately after speaking
                        if (isContinuousMode) {
                            mainHandler.postDelayed({
                                startListening()
                            }, 350)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        onStateUpdate("IDLE", "Standing by.")
                        if (isContinuousMode) {
                            mainHandler.postDelayed({ startListening() }, 500)
                        }
                    }
                })

                // If speech was requested before TTS loaded, speak it now!
                pendingSpeech?.let { text ->
                    val queued = text
                    pendingSpeech = null
                    speak(queued)
                }
            } else {
                onStateUpdate("ERROR", "TTS engine initialization failed.")
            }
        }
    }

    fun startContinuousCadence() {
        isContinuousMode = true
        speak("Nex online. Systems fully operational, Sir.")
    }

    fun stopContinuousCadence() {
        isContinuousMode = false
        stopListening()
        destroyRecognizer()
        speak("Systems powering down. Standing by, Sir.")
    }

    fun startListening() {
        if (isSpeaking) return

        mainHandler.post {
            try {
                destroyRecognizer()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    onStateUpdate("ERROR", "Speech recognizer unavailable on this device.")
                    return@post
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            onStateUpdate("LISTENING", "Listening to your voice, Sir...")
                        }

                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            onStateUpdate("PROCESSING", "Analyzing audio protocols...")
                        }

                        override fun onError(error: Int) {
                            // Silent recovery in continuous mode so it never goes dead
                            if (isContinuousMode && !isSpeaking) {
                                mainHandler.postDelayed({
                                    startListening()
                                }, 500)
                            } else {
                                onStateUpdate("IDLE", "Standing by.")
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val query = matches[0]
                                onStateUpdate("HEARD", query)
                                onSpeechRecognized(query)
                            } else {
                                if (isContinuousMode) {
                                    mainHandler.postDelayed({ startListening() }, 400)
                                }
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                if (isContinuousMode) {
                    mainHandler.postDelayed({ startListening() }, 1000)
                }
            }
        }
    }

    fun speak(text: String) {
        if (!isTtsReady) {
            // Queue speech until TTS finishes loading
            pendingSpeech = text
            return
        }

        mainHandler.post {
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
        isContinuousMode = false
        stopListening()
        destroyRecognizer()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
