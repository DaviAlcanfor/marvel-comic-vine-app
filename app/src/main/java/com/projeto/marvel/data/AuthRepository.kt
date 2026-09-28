package com.projeto.marvel.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** Login no Firebase Auth (e-mail/senha ou Google). A sessão fica salva pelo próprio Firebase. */
class AuthRepository {

    // getInstance() lança IllegalStateException se o app não tiver o google-services.json
    // (Firebase não inicializado). `lazy` não guarda falha: tenta de novo a cada acesso.
    private val auth by lazy { FirebaseAuth.getInstance() }

    val isLoggedIn: Boolean
        get() = runCatching { auth.currentUser != null }.getOrDefault(false)

    suspend fun signIn(email: String, password: String): Result<Unit> =
        runCatching { auth.signInWithEmailAndPassword(email, password).await() }.map { }

    suspend fun signUp(email: String, password: String): Result<Unit> =
        runCatching { auth.createUserWithEmailAndPassword(email, password).await() }.map { }

    /** [idToken] vem do seletor de contas do Google (Credential Manager), na View. */
    suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        runCatching { auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }.map { }

    fun signOut() {
        runCatching { auth.signOut() }
    }
}
