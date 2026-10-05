package com.projeto.marvel.ui.detail

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.projeto.marvel.R
import java.util.Locale

/**
 * "Ouvir" na bio do Detalhe: a voz do Android lê o texto (em inglês, como vem da Comic Vine) e para
 * quando a tela some.
 */
fun Fragment.bindBioActions(bio: TextView, listenButton: AppCompatButton) {
    val context = requireContext()
    var speaking = false
    var tts: TextToSpeech? = null
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
        voice.language = Locale.US
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
