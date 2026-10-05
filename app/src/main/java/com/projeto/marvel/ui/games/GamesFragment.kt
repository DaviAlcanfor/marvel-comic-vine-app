package com.projeto.marvel.ui.games

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import com.projeto.marvel.R
import com.projeto.marvel.data.AchievementStore
import com.projeto.marvel.data.ReadingStore
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.GameRecord
import com.projeto.marvel.data.GameRecordStore
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.databinding.FragmentGamesBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.comicDialog
import com.projeto.marvel.ui.home.heroOfTheDay
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.projeto.marvel.ui.eraEnter

/**
 * Aba Jogos: capa de cada jogo com o que é, como joga e o seu recorde. A capa do "Quem é esse
 * herói?" é a foto do herói do dia já pixelada (dá o clima do jogo sem entregar ninguém novo).
 */
class GamesFragment : Fragment(R.layout.fragment_games) {

    private var binding: FragmentGamesBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentGamesBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        val context = requireContext()
        binding.guessCaption.comicBox(BoxStyle.CAPTION, ContextCompat.getColor(context, R.color.accent))
        binding.guessCaption.rotation = -CAPTION_TILT
        binding.quizCaption.comicBox(BoxStyle.THOUGHT, ContextCompat.getColor(context, R.color.sfx_blue))
        binding.quizCaption.rotation = CAPTION_TILT
        binding.guessPlay.setOnClickListener { findNavController().navigate(R.id.guessFragment) }
        binding.quizPlay.setOnClickListener { findNavController().navigate(R.id.quizFragment) }
        binding.lookCaption.comicBox(BoxStyle.CAPTION, ContextCompat.getColor(context, R.color.balloon_pink))
        binding.lookPlay.setOnClickListener { findNavController().navigate(R.id.lookAlikeFragment) }
        // Capa na meia largura da grade: legenda pequena para caber o nome inteiro do jogo.
        binding.trunfoCaption.comicBox(BoxStyle.CAPTION, ContextCompat.getColor(context, R.color.balloon_red))
        binding.memoryCaption.comicBox(BoxStyle.CAPTION, ContextCompat.getColor(context, R.color.balloon_green))
        binding.quoteCaption.comicBox(BoxStyle.SPEECH, ContextCompat.getColor(context, R.color.white))
        listOf(
            binding.guessCaption, binding.lookCaption, binding.quizCaption,
            binding.trunfoCaption, binding.memoryCaption, binding.quoteCaption
        ).forEach { it.textSize = TILE_CAPTION_SP }
        mapOf(
            binding.trunfoPlay to R.id.trunfoFragment,
            binding.memoryPlay to R.id.memoryFragment,
            binding.quotePlay to R.id.quoteFragment
        ).forEach { (button, destination) -> button.setOnClickListener { findNavController().navigate(destination) } }
        listOf(
            binding.guessInfo, binding.lookInfo, binding.quizInfo,
            binding.trunfoInfo, binding.memoryInfo, binding.quoteInfo
        ).forEach { info ->
            info.setOnClickListener {
                context.comicDialog()
                    .setTitle(R.string.games_how_to)
                    .setMessage(info.tag as String)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch { loadBanners(binding) }
        (binding.root.getChildAt(0) as ViewGroup).eraEnter()
    }

    override fun onResume() {
        super.onResume()
        val binding = binding ?: return
        val best = AchievementStore(requireContext()).bestGuessStreak()
        binding.guessRecord.text = getString(R.string.games_guess_record, best)
        val records = GameRecordStore(requireContext())
        binding.trunfoRecord.text = getString(R.string.games_trunfo_record, records.best(GameRecord.TRUNFO_WINS) ?: 0)
        binding.memoryRecord.text = records.best(GameRecord.MEMORY_MOVES)
            ?.let { getString(R.string.games_memory_record, it) } ?: getString(R.string.games_memory_new)
        binding.quoteRecord.text = getString(R.string.games_quote_record, records.best(GameRecord.QUOTE_STREAK) ?: 0)
        val hero = ReadingStore(requireContext(), AuthRepository().currentUser?.uid).preferences().hero
        binding.quizRecord.text = hero?.let { getString(R.string.games_quiz_record, it.name) }
            ?: getString(R.string.games_quiz_new)
        hero?.imageUrl?.let { binding.quizBanner.load(it) { crossfade(true) } }
    }

    private suspend fun loadBanners(binding: FragmentGamesBinding) {
        val hero = heroOfTheDay(ComicVineRepository().popularCharacters().getOrNull().orEmpty(), LocalDate.now())
        val url = hero?.image?.mediumUrl ?: return
        val request = ImageRequest.Builder(requireContext()).data(url).allowHardware(false).build()
        val bitmap = (requireContext().imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
        if (bitmap != null && this.binding != null) binding.guessBanner.pixelated(bitmap)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private companion object {
        const val CAPTION_TILT = 3f
    }
}

private const val BANNER_PIXELS = 16

private fun ImageView.pixelated(bitmap: Bitmap) {
    val height = (BANNER_PIXELS * bitmap.height / bitmap.width).coerceAtLeast(1)
    setImageDrawable(
        BitmapDrawable(resources, Bitmap.createScaledBitmap(bitmap, BANNER_PIXELS, height, true))
            .apply { paint.isFilterBitmap = false }
    )
}

private const val TILE_CAPTION_SP = 13f
