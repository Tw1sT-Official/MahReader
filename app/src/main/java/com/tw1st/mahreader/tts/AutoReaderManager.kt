package com.tw1st.mahreader.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class AutoReaderManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isTtsAvailable = MutableStateFlow(true)
    val isTtsAvailable: StateFlow<Boolean> = _isTtsAvailable.asStateFlow()

    private val _isMissingVoiceData = MutableStateFlow(false)
    val isMissingVoiceData: StateFlow<Boolean> = _isMissingVoiceData.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private var onPageCompletedCallback: (() -> Unit)? = null
    private var currentParagraphs: List<String> = emptyList()
    private var currentParagraphIndex: Int = 0

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            _isTtsAvailable.value = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsEngine = tts ?: return
            val engines = ttsEngine.engines
            if (engines.isNullOrEmpty()) {
                _isTtsAvailable.value = false
                return
            }

            val langResult = ttsEngine.isLanguageAvailable(Locale.US)
            if (langResult == TextToSpeech.LANG_MISSING_DATA) {
                _isMissingVoiceData.value = true
            }

            ttsEngine.language = Locale.US
            ttsEngine.setSpeechRate(_speechRate.value)

            ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isPlaying.value = true
                }

                override fun onDone(utteranceId: String?) {
                    handleParagraphDone()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isPlaying.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isPlaying.value = false
                }
            })

            isInitialized = true
            _isTtsAvailable.value = true
        } else {
            _isTtsAvailable.value = false
        }
    }

    private fun handleParagraphDone() {
        val nextIndex = currentParagraphIndex + 1
        if (nextIndex < currentParagraphs.size) {
            currentParagraphIndex = nextIndex
            speakCurrentParagraph()
        } else {
            // Finished entire page! Trigger auto page flip callback
            _isPlaying.value = false
            onPageCompletedCallback?.invoke()
        }
    }

    private fun speakCurrentParagraph() {
        val t = tts ?: return
        if (currentParagraphIndex >= currentParagraphs.size) {
            _isPlaying.value = false
            onPageCompletedCallback?.invoke()
            return
        }

        val text = currentParagraphs[currentParagraphIndex].trim()
        if (text.isEmpty()) {
            handleParagraphDone()
            return
        }

        val params = Bundle()
        t.speak(text, TextToSpeech.QUEUE_FLUSH, params, "para_${currentParagraphIndex}_${System.currentTimeMillis()}")
    }

    fun startReadingPage(pageText: String, onDonePage: () -> Unit) {
        onPageCompletedCallback = onDonePage

        // Clean and split text into manageable paragraphs
        val rawParagraphs = pageText.split("\n\n", "\r\n\r\n", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 2 }

        if (rawParagraphs.isEmpty()) {
            // Illustration or empty page, advance after brief pause
            onDonePage()
            return
        }

        currentParagraphs = rawParagraphs
        currentParagraphIndex = 0

        if (isInitialized && tts != null) {
            _isPlaying.value = true
            speakCurrentParagraph()
        } else {
            initTts()
        }
    }

    fun pause() {
        tts?.stop()
        _isPlaying.value = false
    }

    fun resume() {
        if (currentParagraphIndex < currentParagraphs.size) {
            _isPlaying.value = true
            speakCurrentParagraph()
        }
    }

    fun stop() {
        tts?.stop()
        _isPlaying.value = false
        currentParagraphs = emptyList()
        currentParagraphIndex = 0
    }

    fun setSpeed(rate: Float) {
        _speechRate.value = rate
        tts?.setSpeechRate(rate)
    }

    fun getInstallTtsDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (_: Exception) {}
    }
}
