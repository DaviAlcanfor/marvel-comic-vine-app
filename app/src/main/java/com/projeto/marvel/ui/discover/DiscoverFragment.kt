package com.projeto.marvel.ui.discover

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentDiscoverBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.BurstDrawable
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.characters.CharactersFragment
import com.projeto.marvel.ui.creators.CreatorsFragment
import com.projeto.marvel.ui.locations.LocationsFragment
import com.projeto.marvel.ui.movies.MoviesFragment
import com.projeto.marvel.ui.releases.ReleasesFragment
import com.projeto.marvel.ui.teams.TeamsFragment

/**
 * Aba Descobrir: um lugar só para explorar o universo (personagens, times, criadores, filmes).
 * Cada página recebe [ARG_EMBEDDED] e esconde o próprio título (o daqui já diz onde se está).
 */
class DiscoverFragment : Fragment(R.layout.fragment_discover) {

    // Foto pequena da câmera basta para a IA e dispensa arquivo/FileProvider.
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { photo ->
        photo?.let(::recognizeHero)
    }

    private var binding: FragmentDiscoverBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.shazamButton.setOnClickListener { runCatching { takePhoto.launch(null) } }
        binding.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = PAGES.size
            override fun createFragment(position: Int) =
                PAGES[position].second().apply { arguments = bundleOf(ARG_EMBEDDED to true) }
        }
        // O swipe lateral brigaria com as listas horizontais (membros do time, filmes): só pelas abas.
        binding.pager.isUserInputEnabled = false
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.setText(PAGES[position].first)
        }.attach()

        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.actionBalloon.popCall(CALLS[position])
            }
        })
        binding.actionBalloon.popCall(CALLS[binding.pager.currentItem])
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        const val ARG_EMBEDDED = "embedded"

        /** Balão de ação de cada aba (mesma ordem): frase, caixa, cor, inclinação e deslocamento. */
        private val CALLS = listOf(
            Call(R.string.discover_call_characters, BoxStyle.SPEECH, R.color.move_strike, 1f, -4, 0),
            Call(R.string.discover_call_teams, BoxStyle.SPEECH, R.color.move_water, -2f, -6, 2),
            Call(R.string.discover_call_creators, BoxStyle.CAPTION, R.color.move_heal, 1f, -12, 0),
            Call(R.string.discover_call_movies, BoxStyle.BURST, R.color.move_magic, 2f, -4, -2),
            Call(R.string.discover_call_locations, BoxStyle.SPEECH, R.color.move_dodge, -1f, -8, 2),
            Call(R.string.discover_call_releases, BoxStyle.BURST, R.color.move_strike, -2f, -6, 0)
        )

        private val PAGES: List<Pair<Int, () -> Fragment>> = listOf(
            R.string.discover_characters to ::CharactersFragment,
            R.string.discover_teams to ::TeamsFragment,
            R.string.discover_creators to ::CreatorsFragment,
            R.string.discover_movies to ::MoviesFragment,
            R.string.discover_locations to ::LocationsFragment,
            R.string.discover_releases to ::ReleasesFragment
        )
    }
}

/** Página dentro do Descobrir (esconde o título próprio). */
val Fragment.embedded get() = arguments?.getBoolean(DiscoverFragment.ARG_EMBEDDED) == true

/**
 * Página dentro do Descobrir: sem fundo próprio (a retícula dela começaria em outro ponto e
 * ficaria desalinhada com a do Descobrir) e sem o espaço de topo (o título é o do Descobrir).
 */
fun Fragment.fitInDiscover(root: View, title: View) {
    if (!embedded) return
    root.background = null
    root.updatePadding(top = 0)
    title.visibility = View.GONE
}

private class Call(
    @StringRes val text: Int,
    val style: BoxStyle,
    @ColorRes val color: Int,
    val tilt: Float,
    val shiftXDp: Int,
    val shiftYDp: Int
)

/**
 * Balão de ação: troca frase, caixa e cor e "salta" (encolhe e volta passando do tamanho), já na
 * inclinação e posição da aba. Depois fica parado — não pisca o tempo todo.
 */
private fun TextView.popCall(call: Call) {
    setText(call.text)
    comicBox(call.style, ContextCompat.getColor(context, call.color), tailOnLeft = false)
    val density = resources.displayMetrics.density
    animate().cancel()
    scaleX = POP_START_SCALE
    scaleY = POP_START_SCALE
    animate().scaleX(1f).scaleY(1f)
        .rotation(call.tilt)
        .translationX(call.shiftXDp * density)
        .translationY(call.shiftYDp * density)
        .setDuration(POP_MILLIS)
        .setInterpolator(OvershootInterpolator())
    val burst = background as? BurstDrawable ?: return
    repeat(BOIL_FRAMES) { frame -> postDelayed({ burst.boil() }, frame * BOIL_FRAME_MILLIS) }
}

private const val POP_START_SCALE = 0.4f
private const val POP_MILLIS = 350L
private const val BOIL_FRAMES = 4
private const val BOIL_FRAME_MILLIS = 90L
