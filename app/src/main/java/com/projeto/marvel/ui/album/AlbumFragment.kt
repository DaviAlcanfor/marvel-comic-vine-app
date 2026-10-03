package com.projeto.marvel.ui.album

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import com.projeto.marvel.MainActivity
import com.projeto.marvel.R
import com.projeto.marvel.data.PackType
import com.projeto.marvel.data.Rarity
import com.projeto.marvel.data.levelFor
import com.projeto.marvel.databinding.FragmentAlbumBinding
import com.projeto.marvel.databinding.ItemTradingCardBinding
import com.projeto.marvel.databinding.ViewAlbumHeaderBinding
import com.projeto.marvel.databinding.ViewPackBinding
import com.projeto.marvel.ui.detail.TiltController
import kotlinx.coroutines.launch

class AlbumFragment : Fragment(R.layout.fragment_album) {

    private val viewModel: AlbumViewModel by viewModels()
    private var binding: FragmentAlbumBinding? = null
    private val adapter = StickerAdapter { sticker -> viewModel.openViewer(sticker) }

    // Pacotes e missões: primeira linha da grade (a grade rola e recicla as figurinhas).
    private var header: ViewAlbumHeaderBinding? = null

    // Figurinha na carta em 3D (muda ao evoluir para Divina: aí a frente é redesenhada).
    private var shownSticker: Sticker? = null
    private var motion: PackMotion? = null
    private var viewer: CardViewer? = null
    private val shownCards = mutableListOf<ItemTradingCardBinding>()

    // Pacote já mostrado (a abertura não repete ao voltar para a tela ou girar).
    private var shownOpenId = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentAlbumBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        // Antes de inflar o cabeçalho: a RecyclerView sem LayoutManager não gera LayoutParams (crash).
        binding.grid.layoutManager = GridLayoutManager(requireContext(), COLUMNS).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (position == 0) COLUMNS else 1
            }
        }
        val header = ViewAlbumHeaderBinding.inflate(layoutInflater, binding.grid, false)
        this.header = header
        binding.grid.adapter = ConcatAdapter(SingleViewAdapter(header.root), adapter)
        header.bindFilters(layoutInflater, { viewModel.query.value }, viewModel::setQuery)
        binding.message.setOnClickListener { viewModel.load() }
        viewer = CardViewer(binding.viewerCard, binding.viewerFront, binding.viewerBack)
        binding.viewerClose.setOnClickListener { viewModel.closeViewer() }
        // Inclinar o celular gira o pacote da abertura, as cartas reveladas e a carta em 3D.
        viewLifecycleOwner.lifecycle.addObserver(
            TiltController(requireContext()) { pitch, roll ->
                // Sem carta em 3D nem pacote na tela, não há o que inclinar (o sensor manda ~50/s).
                if (!binding.opening.isVisible && !binding.viewer.isVisible) return@TiltController
                motion?.tilt(pitch, roll)
                binding.openedCards.rotationX = -pitch * CARDS_TILT
                binding.openedCards.rotationY = roll * CARDS_TILT
                shownCards.forEach { it.tilt(pitch, roll) }
                if (binding.viewer.isVisible) viewer?.tilt(pitch, roll)
            }
        )
        binding.keepButton.setOnClickListener {
            binding.opening.fadeOut()
            geek(true)
        }
        header.tiles().forEach { (type, tile, _) ->
            tile.setOnClickListener { viewModel.openPack(type) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refresh()
                launch { viewModel.viewer.collect(::renderViewer) }
                launch {
                    viewModel.query.collect { query ->
                        val album = viewModel.state.value as? AlbumUiState.Success ?: return@collect
                        this@AlbumFragment.header?.submit(adapter, album.stickers, query)
                    }
                }
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: AlbumUiState) {
        val binding = binding ?: return
        binding.loading.isVisible = state is AlbumUiState.Loading
        binding.message.isVisible = state is AlbumUiState.Error
        binding.grid.isVisible = state is AlbumUiState.Success
        if (state is AlbumUiState.Error) {
            binding.message.text = getString(R.string.home_error_retry) + "\n" + state.message
        }
        if (state !is AlbumUiState.Success) return
        // Criado junto com a binding (e zerado junto): com a binding viva, o cabeçalho existe.
        val header = requireNotNull(header)
        header.progress.text = getString(R.string.album_progress, state.owned, state.stickers.size)
        header.tiles().forEach { (type, tile, pack) ->
            val count = state.packs[type] ?: 0
            pack.style(type, large = false, art = state.packArt[type])
            tile.isEnabled = count > 0
            tile.alpha = if (count > 0) 1f else DISABLED_ALPHA
            (tile.getChildAt(1) as TextView).text = getString(R.string.album_pack_count, getString(type.label()), count)
            if (count > 0) pack.gleam()
        }
        header.submit(adapter, state.stickers, viewModel.query.value)
        if (state.openId != shownOpenId && state.opened.isNotEmpty()) {
            shownOpenId = state.openId
            showOpening(binding, state)
        }
    }

    /** Pacote grande interativo ([PackMotion]); rasgado, as figurinhas viram uma a uma. */
    private fun showOpening(binding: FragmentAlbumBinding, state: AlbumUiState.Success) {
        val pack = binding.bigPack
        val type = state.openedType
        pack.reset()
        pack.style(type, large = true, art = state.packArt[type])
        pack.root.isVisible = true
        binding.openingTitle.setText(type.label())
        binding.openingHint.isVisible = true
        binding.openedCards.isVisible = false
        binding.keepButton.isVisible = false
        binding.opening.fadeIn()
        geek(false)
        motion?.stop()
        motion = PackMotion(pack) {
            pack.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            binding.openingHint.isVisible = false
            pack.tear {
                pack.root.isVisible = false
                binding.flash.flash()
                revealCards(binding, state.opened)
            }
        }.also { it.start() }
    }

    /** As 4 figurinhas (2×2) entram de costas na cor da raridade e viram uma depois da outra. */
    private fun revealCards(binding: FragmentAlbumBinding, cards: List<Sticker>) {
        binding.openedCards.isVisible = true
        listOf(binding.cardsTop, binding.cardsBottom).forEach { it.removeAllViews() }
        shownCards.clear()
        cards.forEachIndexed { index, sticker ->
            val row = if (index < cards.size / 2) binding.cardsTop else binding.cardsBottom
            val card = ItemTradingCardBinding.inflate(layoutInflater, row, false)
            row.addView(card.root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            shownCards += card
            card.bind(sticker, revealed = false)
            card.root.flipIn(index * STAGGER_MILLIS) {
                card.bind(sticker, revealed = true, large = true)
                if (sticker.rarity == Rarity.LEGENDARY) card.root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                // Já virada, a carta abre grande em 3D como as do álbum.
                card.root.setOnClickListener { viewModel.openViewer(sticker) }
            }
        }
        binding.keepButton.visibility = View.VISIBLE
        binding.keepButton.alpha = 0f
        binding.keepButton.animate().alpha(1f).setStartDelay(cards.size * STAGGER_MILLIS)
    }

    /** Figurinha tocada na grade: grande, em 3D, com o verso de informações. */
    private fun showCard(binding: FragmentAlbumBinding, sticker: Sticker, animate: Boolean) {
        binding.viewerFront.bind(sticker, revealed = true, large = true)
        binding.aura.setRarity(sticker.rarity, sticker.golden)
        // Verso no mesmo metal da frente, com texto escuro (o metal é claro).
        binding.viewerBack.background = sticker.rarity.metalBackground(requireContext())
        val ink = ContextCompat.getColor(requireContext(), R.color.ink)
        binding.backName.setTextColor(ink)
        binding.backInfo.setTextColor(ink)
        val rarity = when (sticker.rarity) {
            Rarity.COMMON -> R.string.album_rarity_common
            Rarity.RARE -> R.string.album_rarity_rare
            Rarity.LEGENDARY -> R.string.album_rarity_legendary
        }
        val character = sticker.character
        binding.backNumber.text = getString(R.string.album_number, sticker.number)
        binding.backName.text = character.name
        binding.backInfo.text = getString(
            R.string.album_back_info,
            getString(rarity),
            listOfNotNull(
                character.realName,
                character.issueAppearances?.let { getString(R.string.album_back_appearances, it) }
            ).joinToString("\n"),
            sticker.count,
            levelFor(sticker.count)
        )
        binding.backOpen.setOnClickListener {
            findNavController().navigate(
                R.id.characterDetailFragment,
                bundleOf("apiDetailUrl" to character.apiDetailUrl, "characterName" to character.name)
            )
        }
        if (!animate) return
        viewer?.reset()
        if (!binding.viewer.isVisible) binding.viewer.fadeIn()
        binding.viewerCard.scaleX = POP_SCALE
        binding.viewerCard.scaleY = POP_SCALE
        binding.viewerCard.animate().scaleX(1f).scaleY(1f).setDuration(POP_MILLIS)
            .setInterpolator(android.view.animation.OvershootInterpolator())
    }

    /** Carta em 3D + painel de melhorias; null = fechada. */
    private fun renderViewer(card: CardUpgrade?) {
        val binding = binding ?: return
        geek(card == null && !binding.opening.isVisible)
        if (card == null) {
            shownSticker = null
            if (binding.viewer.isVisible) binding.viewer.fadeOut()
            return
        }
        if (card.sticker != shownSticker) {
            // Outra carta: o painel de atributos começa fechado.
            if (shownSticker?.character?.id != card.sticker.character.id) binding.upgradePanel.isVisible = false
            val evolved = shownSticker?.let { !it.golden && card.sticker.golden } == true
            showCard(binding, card.sticker, animate = shownSticker == null || evolved)
            if (evolved) binding.root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            shownSticker = card.sticker
        }
        binding.bindUpgrade(card, viewModel::allocate, viewModel::evolve)
    }

    private fun geek(visible: Boolean) = (activity as? MainActivity)?.setGeekVisible(visible)

    override fun onDestroyView() {
        super.onDestroyView()
        motion?.stop()
        motion = null
        viewer = null
        shownSticker = null
        shownCards.clear()
        header = null
        binding = null
    }

    private companion object {
        const val DISABLED_ALPHA = 0.45f
        const val STAGGER_MILLIS = 450L
        const val CARDS_TILT = 1.2f
        const val POP_SCALE = 0.6f
        const val POP_MILLIS = 350L
        const val COLUMNS = 3
    }
}

private fun ViewAlbumHeaderBinding.tiles(): List<Triple<PackType, LinearLayout, ViewPackBinding>> = listOf(
    Triple(PackType.BASIC, tileBasic, packBasic),
    Triple(PackType.SILVER, tileSilver, packSilver),
    Triple(PackType.GOLD, tileGold, packGold)
)

private const val QUARTER_TURN = 90f
private const val CAMERA_DISTANCE = 8_000f
private const val FLIP_HALF_MILLIS = 200L
private const val FLASH_MILLIS = 360L
private const val FLASH_ALPHA = 0.8f
private const val FADE_MILLIS = 200L

/** Meia volta até ficar de lado, [onEdge] troca a face, e completa a volta. */
private fun View.flipIn(delay: Long, onEdge: () -> Unit) {
    cameraDistance = CAMERA_DISTANCE * resources.displayMetrics.density
    rotationY = 0f
    animate().rotationY(QUARTER_TURN).setStartDelay(delay).setDuration(FLIP_HALF_MILLIS).withEndAction {
        onEdge()
        rotationY = -QUARTER_TURN
        animate().rotationY(0f).setStartDelay(0).setDuration(FLIP_HALF_MILLIS)
    }
}

/** Clarão branco rápido quando o pacote abre. */
private fun View.flash() {
    alpha = FLASH_ALPHA
    animate().alpha(0f).setStartDelay(0).setDuration(FLASH_MILLIS)
}

private fun View.fadeIn() {
    alpha = 0f
    visibility = View.VISIBLE
    animate().alpha(1f).setStartDelay(0).setDuration(FADE_MILLIS)
}

private fun View.fadeOut() {
    animate().alpha(0f).setStartDelay(0).setDuration(FADE_MILLIS).withEndAction { visibility = View.GONE }
}
