package com.projeto.marvel.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentChatBinding
import com.projeto.marvel.ui.fadeVisible
import kotlinx.coroutines.launch

/** Chat com o Geek (agente que consulta a Comic Vine, ver data/GeekAgent.kt). */
class ChatFragment : Fragment(R.layout.fragment_chat) {

    private val viewModel: ChatViewModel by viewModels()
    private var binding: FragmentChatBinding? = null
    private val adapter = ChatMessageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentChatBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.name.setText(R.string.geek_name)
        binding.avatar.setImageResource(R.drawable.ic_geek)
        binding.typing.text = getString(R.string.chat_typing, getString(R.string.geek_name))
        binding.messages.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        binding.messages.adapter = adapter
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.sendButton.setOnClickListener {
            viewModel.send(binding.input.text.toString())
            binding.input.text.clear()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: ChatUiState) {
        val binding = binding ?: return
        binding.progressBar.visibility = View.GONE
        binding.errorText.visibility = View.GONE
        binding.typing.fadeVisible(state.typing)
        binding.sendButton.isEnabled = !state.typing
        adapter.submitList(state.messages) {
            if (state.messages.isNotEmpty()) binding.messages.smoothScrollToPosition(state.messages.lastIndex)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        /** Abre o Geek já perguntando sobre [topic] (ex. o personagem do Detalhe). */
        fun args(topic: String? = null) = bundleOf(ChatViewModel.ARG_TOPIC to topic)
    }
}
