package com.poetry.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 诗词朗读管理器：封装系统 TextToSpeech，播放诗词正文。
 * onStart/onDone 回调用于切换按钮状态（播放/停止）。
 */
class TtsManager(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onDone: () -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null   // 初始化完成前缓存的待朗读文本

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        ready = (status == TextToSpeech.SUCCESS)
        if (ready) {
            tts?.language = Locale.CHINESE
            tts?.setSpeechRate(0.85f)   // 诗词朗读放慢语速
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { onStart() }
                override fun onDone(utteranceId: String?) { onDone() }
                override fun onError(utteranceId: String?) { onDone() }
            })
            pending?.let { speak(it) }
            pending = null
        }
    }

    /** 朗读文本（先停止上一次） */
    fun speak(text: String) {
        if (!ready) { pending = text; return }
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "poem_utterance")
    }

    /** 停止朗读 */
    fun stop() {
        if (ready) tts?.stop()
        pending = null
    }

    fun isSpeaking(): Boolean = ready && (tts?.isSpeaking == true)

    /** 释放资源，Activity 销毁时调用 */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}