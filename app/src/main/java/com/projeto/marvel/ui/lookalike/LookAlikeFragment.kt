package com.projeto.marvel.ui.lookalike

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
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
import com.projeto.marvel.databinding.FragmentLookAlikeBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.photo.shareImage
import kotlinx.coroutines.launch

/** "Com qual herói você parece?" (aparência, pela IA); o quiz é o de personalidade. */
class LookAlikeFragment : Fragment(R.layout.fragment_look_alike) {

    private val viewModel: LookAlikeViewModel by viewModels()
    private var binding: FragmentLookAlikeBinding? = null

    // Foto pequena da câmera já basta para a IA e não precisa de arquivo/FileProvider.
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { photo ->
        photo?.let(viewModel::analyze)
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.analyze(decode(it)) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentLookAlikeBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.cameraButton.setOnClickListener { takePhoto.launch(null) }
        binding.galleryButton.setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: LookAlikeUiState) {
        val binding = binding ?: return
        val photo = when (state) {
            is LookAlikeUiState.Analyzing -> state.photo
            is LookAlikeUiState.Done -> state.photo
            is LookAlikeUiState.Error -> state.photo
            LookAlikeUiState.Idle -> null
        }
        // Com resultado, a foto vai para o card (ao lado do herói); antes, aparece sozinha.
        binding.photo.isVisible = photo != null && state !is LookAlikeUiState.Done
        photo?.let(binding.photo::setImageBitmap)
        binding.analyzing.isVisible = state is LookAlikeUiState.Analyzing
        binding.cameraButton.isEnabled = state !is LookAlikeUiState.Analyzing
        binding.galleryButton.isEnabled = state !is LookAlikeUiState.Analyzing
        binding.message.isVisible = state is LookAlikeUiState.Error
        if (state is LookAlikeUiState.Error) binding.message.text = state.message
        binding.result.isVisible = state is LookAlikeUiState.Done
        binding.resultActions.isVisible = state is LookAlikeUiState.Done
        if (state is LookAlikeUiState.Done) bindResult(binding, state)
    }

    private fun bindResult(binding: FragmentLookAlikeBinding, state: LookAlikeUiState.Done) {
        val result = state.result
        val character = result.character
        binding.yourPhoto.setImageBitmap(state.photo)
        // Bitmap comum (não "hardware"): o card vira imagem no compartilhar.
        binding.heroImage.load(character.image?.mediumUrl) {
            crossfade(true)
            allowHardware(false)
        }
        binding.heroName.text = getString(R.string.look_alike_result, character.name)
        binding.heroInfo.text = listOfNotNull(
            character.realName,
            character.issueAppearances?.let { getString(R.string.look_alike_appearances, it) }
        ).joinToString(" · ")
        binding.matchText.text = getString(R.string.look_alike_match, result.match)
        binding.matchBar.setProgressCompat(result.match, true)
        binding.traits.isVisible = result.traits.isNotEmpty()
        binding.traits.text = result.traits.joinToString("\n") { "• $it" }
        binding.reason.text = result.reason
        binding.quote.isVisible = result.quote.isNotBlank()
        binding.quote.text = getString(R.string.look_alike_quote, result.quote)
        binding.quote.comicBox(BoxStyle.SPEECH, ContextCompat.getColor(requireContext(), R.color.accent))
        binding.runnerUp.isVisible = result.runnerUp != null
        binding.runnerUp.text = result.runnerUp?.let { getString(R.string.look_alike_runner_up, it.name) }
        binding.openHero.setOnClickListener {
            findNavController().navigate(
                R.id.characterDetailFragment,
                bundleOf("apiDetailUrl" to character.apiDetailUrl, "characterName" to character.name)
            )
        }
        binding.shareResult.setOnClickListener {
            shareImage(requireContext(), binding.result.drawToBitmap(), getString(R.string.look_alike_share))
        }
    }

    /** Foto da galeria reduzida (lado maior [MAX_SIDE]) e em memória comum, como a IA precisa. */
    private fun decode(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(requireContext().contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private companion object {
        const val MAX_SIDE = 768
    }
}
