package com.projeto.marvel.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.projeto.marvel.ui.battle.BattleViewModel
import com.projeto.marvel.ui.chat.ChatViewModel
import com.projeto.marvel.ui.comics.ComicSearchViewModel
import com.projeto.marvel.ui.creators.CreatorsViewModel
import com.projeto.marvel.ui.movies.MoviesViewModel
import com.projeto.marvel.ui.detail.CharacterDetailViewModel
import com.projeto.marvel.ui.home.HomeViewModel
import com.projeto.marvel.ui.info.InfoDetailViewModel
import com.projeto.marvel.ui.characters.CharactersViewModel
import com.projeto.marvel.ui.profile.ProfileViewModel
import com.projeto.marvel.ui.album.AlbumViewModel
import com.projeto.marvel.ui.compare.CompareViewModel
import com.projeto.marvel.ui.guess.GuessViewModel
import com.projeto.marvel.ui.quiz.QuizViewModel
import com.projeto.marvel.ui.teams.TeamDetailViewModel
import org.junit.Test

/**
 * O factory padrão de `by viewModels()` só encontra construtores com assinatura exata
 * `(SavedStateHandle)` ou `(Application, SavedStateHandle)`. Parâmetro Kotlin com valor
 * default não gera essa sobrecarga sem `@JvmOverloads` — e a tela crasha ao abrir.
 */
class ViewModelConstructorsTest {

    @Test
    fun `detalhe tem construtor que o factory padrao encontra`() {
        CharacterDetailViewModel::class.java.getConstructor(SavedStateHandle::class.java)
    }

    @Test
    fun `detalhe do time tem construtor que o factory padrao encontra`() {
        TeamDetailViewModel::class.java.getConstructor(SavedStateHandle::class.java)
    }

    @Test
    fun `inicio e herois tem construtor que o factory padrao encontra`() {
        HomeViewModel::class.java.getConstructor(Application::class.java)
        CharactersViewModel::class.java.getConstructor(Application::class.java)
    }

    @Test
    fun `perfil e busca de hqs tem construtor que o factory padrao encontra`() {
        ProfileViewModel::class.java.getConstructor(Application::class.java)
        ComicSearchViewModel::class.java.getConstructor(Application::class.java)
    }

    @Test
    fun `chat tem construtor que o factory padrao encontra`() {
        ChatViewModel::class.java.getConstructor(Application::class.java, SavedStateHandle::class.java)
    }

    @Test
    fun `detalhe de criador e filme tem construtor que o factory padrao encontra`() {
        InfoDetailViewModel::class.java.getConstructor(Application::class.java, SavedStateHandle::class.java)
    }

    @Test
    fun `criadores e filmes tem construtor que o factory padrao encontra`() {
        CreatorsViewModel::class.java.getConstructor(Application::class.java)
        MoviesViewModel::class.java.getConstructor(Application::class.java)
    }

    @Test
    fun `quiz tem construtor que o factory padrao encontra`() {
        QuizViewModel::class.java.getConstructor(Application::class.java)
        GuessViewModel::class.java.getConstructor(Application::class.java)
        AlbumViewModel::class.java.getConstructor(Application::class.java)
        CompareViewModel::class.java.getConstructor(SavedStateHandle::class.java)
    }

    @Test
    fun `batalha tem construtor que o factory padrao encontra`() {
        BattleViewModel::class.java.getConstructor(Application::class.java, SavedStateHandle::class.java)
    }
}
