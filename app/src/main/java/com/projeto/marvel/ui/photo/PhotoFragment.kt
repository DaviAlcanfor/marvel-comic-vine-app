package com.projeto.marvel.ui.photo

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.drawToBitmap
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.AchievementStore
import com.projeto.marvel.databinding.FragmentPhotoBinding

/**
 * "Tire foto com esse personagem": foto da câmera ou da galeria, com o personagem como adesivo
 * (arraste para posicionar) e uma legenda de HQ. Sem estado de negócio: não tem ViewModel.
 */
class PhotoFragment : Fragment(R.layout.fragment_photo) {

    private var binding: FragmentPhotoBinding? = null

    // Sobrevive a rotação: o app de câmera pode recriar esta tela enquanto está aberto.
    private var cameraUri: Uri? = null
    private var photoUri: Uri? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraUri?.let(::showPhoto)
    }
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::showPhoto)
    }

    private val characterName get() = arguments?.getString(ARG_NAME).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraUri = savedInstanceState?.getString(KEY_CAMERA)?.let(Uri::parse)
        photoUri = savedInstanceState?.getString(KEY_PHOTO)?.let(Uri::parse)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentPhotoBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.title.text = getString(R.string.photo_title, characterName)
        binding.caption.text = getString(R.string.photo_caption, characterName)
        // Sem bitmap "de hardware": o quadro é desenhado num Bitmap comum para salvar/compartilhar.
        binding.sticker.load(arguments?.getString(ARG_IMAGE)) { allowHardware(false) }
        binding.sticker.makeDraggable()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.cameraButton.setOnClickListener { openCamera() }
        binding.galleryButton.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.saveButton.setOnClickListener {
            val saved = saveToGallery(requireContext(), binding.frame.drawToBitmap())
            toast(if (saved != null) R.string.photo_saved else R.string.photo_save_failed)
            if (saved != null) AchievementStore(requireContext()).addPhoto()
        }
        binding.shareButton.setOnClickListener {
            shareImage(requireContext(), binding.frame.drawToBitmap(), getString(R.string.photo_share))
            AchievementStore(requireContext()).addPhoto()
        }
        photoUri?.let(::showPhoto)
    }

    private fun openCamera() {
        val uri = newCameraUri(requireContext())
        cameraUri = uri
        try {
            takePicture.launch(uri)
        } catch (_: ActivityNotFoundException) {
            toast(R.string.photo_no_camera)
        }
    }

    private fun showPhoto(uri: Uri) {
        photoUri = uri
        val binding = binding ?: return
        binding.photo.load(uri) { allowHardware(false) }
        binding.emptyHint.visibility = View.GONE
        binding.saveButton.isEnabled = true
        binding.shareButton.isEnabled = true
    }

    /** Arrastar o adesivo pelo quadro (sem sair dele). */
    @SuppressLint("ClickableViewAccessibility") // arrastar não é um clique; o adesivo não tem ação de toque
    private fun View.makeDraggable() {
        var lastX = 0f
        var lastY = 0f
        setOnTouchListener { view, event ->
            val parent = view.parent as View
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    view.x = (view.x + event.rawX - lastX).coerceIn(0f, (parent.width - view.width).toFloat())
                    view.y = (view.y + event.rawY - lastY).coerceIn(0f, (parent.height - view.height).toFloat())
                    lastX = event.rawX
                    lastY = event.rawY
                }
            }
            true
        }
    }

    private fun toast(message: Int) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CAMERA, cameraUri?.toString())
        outState.putString(KEY_PHOTO, photoUri?.toString())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        private const val ARG_NAME = "characterName"
        private const val ARG_IMAGE = "imageUrl"
        private const val KEY_CAMERA = "cameraUri"
        private const val KEY_PHOTO = "photoUri"

        fun args(name: String, imageUrl: String?) = bundleOf(ARG_NAME to name, ARG_IMAGE to imageUrl)
    }
}
