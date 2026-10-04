package com.projeto.marvel.ui.detail

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.projeto.marvel.R
import com.projeto.marvel.data.BioTranslator
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * "Traduzir" (Gemini, com cache) e "Ouvir" (voz do Android) na bio do Detalhe. A voz fala em
 * português depois de traduzir, senão em inglês; para sozinha quando a tela some.
 */
fun Fragment.bindBioActions(
    characterId: Int,
    bio: TextView,
    translateButton: AppCompatButton,
    listenButton: AppCompatButton
) {
    val context = requireContext()
    val translator = BioTranslator(context)
    var portuguese = false
    var speaking = false
    var tts: TextToSpeech? = null
    translator.cached(characterId)?.let {
        bio.text = it
        portuguese = true
        translateButton.isClickable = false
        translateButton.setText(R.string.detail_translated)
    }
    translateButton.setOnClickListener {
        translateButton.isClickable = false
        translateButton.setText(R.string.detail_translating)
        viewLifecycleOwner.lifecycleScope.launch {
            translator.translate(characterId, bio.text.toString())
                .onSuccess {
                    bio.text = it
                    portuguese = true
                    translateButton.setText(R.string.detail_translated)
                }
                .onFailure {
                    translateButton.isClickable = true
                    translateButton.setText(R.string.detail_translate)
                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                }
        }
    }
    listenButton.setOnClickListener {
        val voice = tts ?: TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) listenButton.performClick()
        }.also { created ->
            tts = created
            created.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        listenButton.post {
                            speaking = false
                            listenButton.setText(R.string.detail_listen)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = onDone(utteranceId)
                }
            )
        }
        if (speaking) {
            voice.stop()
            speaking = false
            listenButton.setText(R.string.detail_listen)
            return@setOnClickListener
        }
        voice.language = if (portuguese) Locale.forLanguageTag("pt-BR") else Locale.US
        if (voice.speak(bio.text, TextToSpeech.QUEUE_FLUSH, null, "bio") == TextToSpeech.SUCCESS) {
            speaking = true
            listenButton.setText(R.string.detail_stop)
        }
    }
    viewLifecycleOwner.lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                tts?.shutdown()
                tts = null
            }
        }
    )
}
