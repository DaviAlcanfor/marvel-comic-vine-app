package com.projeto.marvel.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.projeto.marvel.ui.battle.BattleViewModel
import com.projeto.marvel.ui.detail.CharacterDetailViewModel
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
    fun `batalha tem construtor que o factory padrao encontra`() {
        BattleViewModel::class.java.getConstructor(Application::class.java, SavedStateHandle::class.java)
    }
}
