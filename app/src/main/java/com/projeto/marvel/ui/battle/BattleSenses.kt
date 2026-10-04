package com.projeto.marvel.ui.battle

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.projeto.marvel.R
import com.projeto.marvel.data.Move
import com.projeto.marvel.databinding.FragmentBattleBinding
import java.util.Locale
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A luta com o corpo e a voz: locutor que narra cada evento (voz do Android, frases de
 * [commentary]), sacudir o celular para carregar o golpe ([shakeBoost]) e falar o nome do golpe
 * (reconhecimento de voz do sistema, sem permissão de microfone no app). Criado como campo do
 * Fragment: o launcher de voz precisa ser registrado antes da tela começar.
 */
class BattleSenses(private val fragment: Fragment, private val viewModelOf: () -> BattleViewModel) :
    DefaultLifecycleObserver, SensorEventListener {

    private val viewModel get() = viewModelOf()
    private var binding: FragmentBattleBinding? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var charge = 0

    private val context: Context get() = fragment.requireContext()
    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var narrator: Boolean
        get() = prefs.getBoolean(KEY_NARRATOR, false)
        set(value) = prefs.edit { putBoolean(KEY_NARRATOR, value) }

    private val listen = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        val game = viewModel.state.value as? BattleUiState.Success ?: return@registerForActivityResult
        val chooser = game.combatant(game.choosing)
        val ultimate = chooser.fighter.ultimateMove().takeIf { chooser.ultimateReady }
        val move = heard.firstNotNullOfOrNull { moveForSpeech(it, chooser.fighter.moves, ultimate) }
        if (move != null) {
            use(move)
        } else {
            Toast.makeText(context, R.string.battle_voice_unknown, Toast.LENGTH_SHORT).show()
        }
    }

    fun bind(binding: FragmentBattleBinding) {
        this.binding = binding
        fragment.viewLifecycleOwner.lifecycle.addObserver(this)
        showNarrator(binding)
        binding.narratorButton.setOnClickListener {
            narrator = !narrator
            showNarrator(binding)
            if (narrator) speak(context.getString(R.string.battle_narrator_hello))
        }
        binding.micButton.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.battle_voice_prompt))
            runCatching { listen.launch(intent) }
                .onFailure { Toast.makeText(context, R.string.battle_voice_missing, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun showNarrator(binding: FragmentBattleBinding) {
        binding.narratorButton.alpha = if (narrator) 1f else OFF_ALPHA
        binding.narratorButton.contentDescription =
            context.getString(if (narrator) R.string.battle_narrator_off else R.string.battle_narrator_on)
    }

    /** Golpe escolhido (botão ou voz): leva a carga do sacudir e zera. */
    fun use(move: Move) {
        viewModel.use(move, charge)
        charge = 0
    }

    /** Evento que acabou de ser animado: o locutor fala (se ligado). */
    fun onEvent(game: BattleUiState.Success, event: BattleEvent) {
        if (!narrator) return
        val actor = game.combatant(event.side).fighter.name
        val other = game.combatant(event.side.opponent()).fighter.name
        val winner = game.winner?.let { game.combatant(it).fighter.name }
        val pick = Random.nextInt(PICKS)
        speak(
            when {
                winner != null -> knockoutLine(winner, pick)
                // Na troca do 3×3 o nome de quem entra vem no evento (o ativo ainda é o antigo).
                event.outcome == Outcome.SWAP_IN -> commentary(event.outcome, event.moveName.orEmpty(), other)
                else -> commentary(event.outcome, actor, other, event.critical, pick = pick)
            }
        )
    }

    private fun speak(line: String) {
        val voice = tts ?: TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.forLanguageTag("pt-BR")
                tts?.setSpeechRate(SPEECH_RATE)
                tts?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "battle")
            }
        }.also { tts = it }
        if (ttsReady) voice.speak(line, TextToSpeech.QUEUE_FLUSH, null, "battle")
    }

    override fun onResume(owner: LifecycleOwner) {
        val sensors = context.getSystemService(SensorManager::class.java) ?: return
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        context.getSystemService(SensorManager::class.java)?.unregisterListener(this)
        tts?.stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        tts?.shutdown()
        tts = null
        ttsReady = false
        binding = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val game = viewModel.state.value as? BattleUiState.Success
        val (x, y, z) = event.values
        val boost = shakeBoost(sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH)
        val canCharge = game != null && !game.busy && game.winner == null && !game.pvp
        if (!canCharge || boost <= charge) return
        charge = boost
        binding?.turnBanner?.popBanner(context.getString(R.string.battle_charged, charge))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val PREFS_NAME = "battle_senses"
        const val KEY_NARRATOR = "narrator"
        const val OFF_ALPHA = 0.45f
        const val PICKS = 6
        const val SPEECH_RATE = 1.15f
    }
}
