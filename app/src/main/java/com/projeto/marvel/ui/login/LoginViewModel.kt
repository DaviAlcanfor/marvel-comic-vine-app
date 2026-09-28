package com.projeto.marvel.ui.login

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.projeto.marvel.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object Success : LoginUiState

    /** [attempt] muda a cada falha para o mesmo erro repetido ainda chegar na View (e tremer). */
    data class Error(val message: String, val attempt: Int) : LoginUiState
}

class LoginViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    // Já logado (sessão salva pelo Firebase): vai direto pra Home.
    private val _state = MutableStateFlow(if (repository.isLoggedIn) LoginUiState.Success else LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var attempts = 0

    fun signIn(email: String, password: String) = submit(email, password, repository::signIn)

    fun signUp(email: String, password: String) = submit(email, password, repository::signUp)

    fun signInWithGoogle(idToken: String) = authenticate { repository.signInWithGoogle(idToken) }

    /** Falha no seletor de contas do Google (antes de chegar ao Firebase). */
    fun googleSignInFailed(error: Throwable) {
        _state.value = when (error) {
            is GetCredentialCancellationException -> LoginUiState.Idle
            is NoCredentialException -> LoginUiState.Error("Nenhuma conta Google neste aparelho", ++attempts)
            else -> LoginUiState.Error(authErrorMessage(error), ++attempts)
        }
    }

    private fun submit(email: String, password: String, action: suspend (String, String) -> Result<Unit>) {
        val invalid = validateCredentials(email.trim(), password)
        if (invalid != null) {
            _state.value = LoginUiState.Error(invalid, ++attempts)
            return
        }
        authenticate { action(email.trim(), password) }
    }

    private fun authenticate(action: suspend () -> Result<Unit>) {
        if (_state.value == LoginUiState.Loading) return
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            _state.value = action().fold(
                onSuccess = { LoginUiState.Success },
                onFailure = { LoginUiState.Error(authErrorMessage(it), ++attempts) }
            )
        }
    }
}

private const val MIN_PASSWORD_LENGTH = 6
private val EMAIL_REGEX = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")

/** Validação local antes de ir ao Firebase. Nulo = ok. */
internal fun validateCredentials(email: String, password: String): String? = when {
    email.isBlank() -> "Informe o e-mail"
    !EMAIL_REGEX.matches(email) -> "E-mail inválido"
    password.length < MIN_PASSWORD_LENGTH -> "A senha precisa ter pelo menos $MIN_PASSWORD_LENGTH caracteres"
    else -> null
}

// WeakPassword é subclasse de InvalidCredentials: precisa vir antes no `when`.
private fun authErrorMessage(error: Throwable): String = when (error) {
    is FirebaseAuthWeakPasswordException -> "Senha fraca: use pelo menos $MIN_PASSWORD_LENGTH caracteres"
    is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "E-mail ou senha incorretos"
    is FirebaseAuthUserCollisionException -> "Este e-mail já está cadastrado"
    is FirebaseNetworkException -> "Sem conexão com a internet"
    is IllegalStateException -> "Firebase não configurado (falta o google-services.json)"
    else -> error.message ?: "Falha no login"
}
