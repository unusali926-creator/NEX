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
    var isContinuousMode: Boolean = false
        private set

    init {
        initTTS()
    }

    private fun initTTS() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Natural FRIDAY British Female Cadence
                textToSpeech?.language = Locale.UK

                // Auto-select female British voice if available on device
                val femaleVoice = textToSpeech?.voices?.firstOrNull { voice ->
                    voice.locale.language == "en" &&
                    (voice.name.contains("female", ignoreCase = true) || voice.name.contains("en-gb", ignoreCase = true)) &&
                    !voice.isNetworkConnectionRequired
                }
                if (femaleVoice != null) {
                    textToSpeech?.voice = femaleVoice
                }

                textToSpeech?.setPitch(1.08f) // Clear, polite female tone
                textToSpeech?.setSpeechRate(0.98f) // Sophisticated pacing

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        onStateUpdate("SPEAKING")
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        onStateUpdate("IDLE")
                        // CADENCE LOOP: Jawab bolne ke baad turant sunna shuru kare
                        if (isContinuousMode) {
                            mainHandler.postDelayed({
                                startListening()
                            }, 350)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        onStateUpdate("IDLE")
                        if (isContinuousMode) {
                            mainHandler.postDelayed({ startListening() }, 500)
                        }
                    }
                })
            }
        }
    }

    fun startContinuousCadence() {
        isContinuousMode = true
        speak("Nex online, listening to your commands, Sir.")
    }

    fun stopContinuousCadence() {
        isContinuousMode = false
        stopListening()
        destroyRecognizer()
        speak("Systems entering standby mode, Sir.")
    }

    fun startListening() {
        if (isSpeaking) return

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
                            // Continuous mode me chupchap dobara mic open kare, bar-bar disturb na kare
                            if (isContinuousMode && !isSpeaking) {
                                mainHandler.postDelayed({
                                    startListening()
                                }, 600)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val query = matches[0]
                                onSpeechRecognized(query)
                            } else {
                                onStateUpdate("IDLE")
                                if (isContinuousMode) {
                                    mainHandler.postDelayed({ startListening() }, 500)
                                }
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                // Bilingual Indian English + Hinglish + Hindi recognition
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
                onStateUpdate("IDLE")
                if (isContinuousMode) {
                    mainHandler.postDelayed({ startListening() }, 1000)
                }
            }
        }
    }

    fun speak(text: String) {
        mainHandler.post {
            stopListening()
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "NEX_CADENCE_ID")
            }
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "NEX_CADENCE_ID")
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
