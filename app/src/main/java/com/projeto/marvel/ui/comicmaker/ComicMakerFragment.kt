package com.projeto.marvel.ui.comicmaker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentComicMakerBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.EraPanelDrawable
import com.projeto.marvel.ui.SpeedLinesDrawable
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.comicDialog
import com.projeto.marvel.ui.eraDimen
import com.projeto.marvel.ui.eraOutline
import com.projeto.marvel.ui.photo.saveToGallery
import com.projeto.marvel.ui.photo.shareImage
import com.projeto.marvel.ui.staggerIn
import kotlinx.coroutines.launch

/** Fundo de cada estilo: cor da tinta e se leva linhas de ação (senão, retícula). */
private val BACKGROUNDS = listOf(
    R.color.caption_yellow to false,
    R.color.sfx_blue to true,
    R.color.primary to false,
    R.color.ink to true,
    R.color.pack_tile_silver to false
)

/** Um quadro montado: fundo, o herói num quadrinho dentro do quadro e o balão de fala arrastável. */
private class PanelViews(val root: FrameLayout, val hero: ImageView, val balloon: TextView)

class ComicMakerFragment : Fragment(R.layout.fragment_comic_maker) {

    private val viewModel: ComicMakerViewModel by viewModels()
    private var binding: FragmentComicMakerBinding? = null
    private val panels = mutableListOf<PanelViews>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentComicMakerBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        repeat(COMIC_PANELS) { index -> panels += buildPanel(binding.page, index) }
        staggerIn(panels.map { it.root })
        binding.saveButton.setOnClickListener { export(share = false) }
        binding.shareButton.setOnClickListener { export(share = true) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    private fun buildPanel(page: LinearLayout, index: Int): PanelViews {
        val context = page.context
        val gap = resources.getDimensionPixelSize(R.dimen.space_xs)
        val root = FrameLayout(context).apply {
            clipToOutline = true
            // Só o contorno do quadro por cima (sem fundo nem sombra, que cobririam a cena).
            foreground = GradientDrawable().apply {
                setStroke(context.eraDimen(R.attr.eraInkWidth).toInt(), context.eraOutline())
            }
            setOnClickListener { editPanel(index) }
        }
        page.addView(
            root,
            LinearLayout.LayoutParams(MATCH, resources.getDimensionPixelSize(R.dimen.comic_panel_height))
                .apply { setMargins(gap, gap, gap, gap) }
        )
        val hero = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = EraPanelDrawable(context, EraPanelDrawable.Kind.PANEL)
            clipToOutline = true
        }
        root.addView(
            hero,
            FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END).apply {
                width = (resources.displayMetrics.widthPixels * HERO_WIDTH).toInt()
                setMargins(gap * 2, gap * 2, gap * 2, gap * 2)
            }
        )
        val balloon = TextView(context).apply {
            maxWidth = (resources.displayMetrics.widthPixels * BALLOON_WIDTH).toInt()
            textSize = BALLOON_SP
            comicBox(BoxStyle.SPEECH, ContextCompat.getColor(context, R.color.white))
        }
        root.addView(balloon, FrameLayout.LayoutParams(WRAP, WRAP))
        balloon.dragWithin(root, index)
        return PanelViews(root, hero, balloon)
    }

    private fun render(state: ComicMakerState) {
        state.panels.forEachIndexed { index, panel ->
            val views = panels.getOrNull(index) ?: return@forEachIndexed
            views.root.background = requireContext().panelBackground(panel.background)
            if (views.hero.tag != panel.hero?.id) {
                views.hero.tag = panel.hero?.id
                views.hero.load(panel.hero?.image?.mediumUrl) { crossfade(true) }
            }
            views.balloon.isVisible = panel.speech.isNotEmpty()
            views.balloon.text = panel.speech
            views.root.post {
                val freeX = (views.root.width - views.balloon.width).coerceAtLeast(0)
                val freeY = (views.root.height - views.balloon.height).coerceAtLeast(0)
                views.balloon.translationX = panel.balloonX * freeX
                views.balloon.translationY = panel.balloonY * freeY
            }
        }
    }

    /** Toque no quadro: trocar herói, trocar fundo ou escrever a fala. */
    private fun editPanel(index: Int) {
        val options = arrayOf(
            getString(R.string.comic_maker_hero),
            getString(R.string.comic_maker_background),
            getString(R.string.comic_maker_speech)
        )
        requireContext().comicDialog()
            .setTitle(getString(R.string.comic_maker_panel, index + 1))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickHero(index)
                    1 -> viewModel.nextBackground(index)
                    else -> writeSpeech(index)
                }
            }
            .show()
    }

    private fun pickHero(index: Int) {
        val heroes = viewModel.state.value.heroes
        if (heroes.isEmpty()) return
        requireContext().comicDialog()
            .setTitle(R.string.comic_maker_hero)
            .setItems(heroes.map { it.name }.toTypedArray()) { _, which -> viewModel.setHero(index, heroes[which]) }
            .show()
    }

    private fun writeSpeech(index: Int) {
        val input = EditText(requireContext()).apply {
            setText(viewModel.state.value.panels[index].speech)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = getString(R.string.comic_maker_speech_hint)
        }
        requireContext().comicDialog()
            .setTitle(R.string.comic_maker_speech)
            .setView(input)
            .setPositiveButton(R.string.comic_maker_ok) { _, _ -> viewModel.setSpeech(index, input.text.toString()) }
            .setNegativeButton(R.string.comics_dialog_cancel, null)
            .show()
    }

    /** Arrastar o balão pelo quadro; ao soltar, guarda a posição (fração do espaço livre). */
    @SuppressLint("ClickableViewAccessibility") // arrastar não é clique; o quadro inteiro já abre a edição
    private fun TextView.dragWithin(panel: FrameLayout, index: Int) {
        var startX = 0f
        var startY = 0f
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX - view.translationX
                    startY = event.rawY - view.translationY
                    panel.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (panel.width - view.width).toFloat().coerceAtLeast(0f)
                    val maxY = (panel.height - view.height).toFloat().coerceAtLeast(0f)
                    view.translationX = (event.rawX - startX).coerceIn(0f, maxX)
                    view.translationY = (event.rawY - startY).coerceIn(0f, maxY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val freeX = (panel.width - view.width).coerceAtLeast(1)
                    val freeY = (panel.height - view.height).coerceAtLeast(1)
                    viewModel.moveBalloon(index, view.translationX / freeX, view.translationY / freeY)
                }
            }
            true
        }
    }

    private fun export(share: Boolean) {
        val page = binding?.page ?: return
        val bitmap = page.drawToBitmap()
        viewModel.published()
        if (share) {
            shareImage(requireContext(), bitmap, getString(R.string.comic_maker_share))
        } else {
            val saved = saveToGallery(requireContext(), bitmap) != null
            Toast.makeText(
                requireContext(),
                if (saved) R.string.comic_maker_saved else R.string.comic_maker_save_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        panels.clear()
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val HERO_WIDTH = 0.45f
        const val BALLOON_WIDTH = 0.5f
        const val BALLOON_SP = 14f
    }
}

/** Fundo do quadro: a tinta do estilo com linhas de ação ou retícula por cima. */
private fun Context.panelBackground(index: Int): Drawable {
    val (colorRes, lines) = BACKGROUNDS[index % BACKGROUNDS.size]
    val color = ContextCompat.getColor(this, colorRes)
    val pattern = if (lines) {
        SpeedLinesDrawable(ContextCompat.getColor(this, R.color.white))
    } else {
        requireNotNull(ContextCompat.getDrawable(this, R.drawable.bg_halftone))
    }
    return LayerDrawable(arrayOf(ColorDrawable(color), pattern))
}
