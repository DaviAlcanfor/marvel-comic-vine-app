package com.projeto.marvel.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentLoginBinding
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.shake
import com.projeto.marvel.ui.staggerIn
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {

    private val viewModel: LoginViewModel by viewModels()
    private var binding: FragmentLoginBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentLoginBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)

        binding.loginButton.setOnClickListener {
            viewModel.signIn(binding.emailInput.text.toString(), binding.passwordInput.text.toString())
        }
        binding.googleButton.setOnClickListener { signInWithGoogle() }
        binding.signUpButton.setOnClickListener {
            viewModel.signUp(binding.emailInput.text.toString(), binding.passwordInput.text.toString())
        }

        if (savedInstanceState == null) {
            staggerIn(
                listOf(
                    binding.title,
                    binding.emailInput,
                    binding.passwordInput,
                    binding.loginButton,
                    binding.googleButton,
                    binding.signUpButton
                )
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: LoginUiState) {
        val binding = binding ?: return
        val loading = state is LoginUiState.Loading
        binding.progress.fadeVisible(loading)
        // Texto some enquanto carrega (o indicador fica no lugar dele, dentro do botão).
        binding.loginButton.text = if (loading) "" else getString(R.string.login_button)
        binding.loginButton.isEnabled = !loading
        binding.googleButton.isEnabled = !loading
        binding.signUpButton.isEnabled = !loading
        binding.errorText.fadeVisible(state is LoginUiState.Error)

        when (state) {
            is LoginUiState.Error -> {
                binding.errorText.text = state.message
                binding.emailInput.shake()
                binding.passwordInput.shake()
            }
            LoginUiState.Success -> findNavController().navigate(R.id.action_login_to_home)
            LoginUiState.Idle, LoginUiState.Loading -> Unit
        }
    }

    /** Abre o seletor de contas do Google (precisa da Activity) e manda o token pra ViewModel. */
    private fun signInWithGoogle() {
        // `default_web_client_id` é gerado pelo plugin google-services a partir do JSON. Sem o JSON
        // o recurso não existe, então é buscado pelo nome em vez de R.string (que não compilaria).
        @Suppress("DiscouragedApi")
        val clientIdRes = resources.getIdentifier("default_web_client_id", "string", requireContext().packageName)
        if (clientIdRes == 0) {
            viewModel.googleSignInFailed(IllegalStateException("default_web_client_id ausente"))
            return
        }
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(getString(clientIdRes)).build())
            .build()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val result = CredentialManager.create(requireContext()).getCredential(requireActivity(), request)
                GoogleIdTokenCredential.createFrom(result.credential.data).idToken
            }.fold(
                onSuccess = viewModel::signInWithGoogle,
                onFailure = viewModel::googleSignInFailed
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
