package com.projeto.marvel.ui.discover

import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.projeto.marvel.R
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.HeroShazam
import com.projeto.marvel.data.PackType
import com.projeto.marvel.data.StickerStore
import com.projeto.marvel.data.remote.CharacterSummary
import kotlinx.coroutines.launch

/** Foto da câmera → personagem → Detalhe; o 1º reconhecimento do dia vale figurinha (ou pacote). */
fun Fragment.recognizeHero(photo: Bitmap) {
    toast(getString(R.string.shazam_working))
    viewLifecycleOwner.lifecycleScope.launch {
        val shazam = HeroShazam(requireContext())
        shazam.recognize(photo)
            .onSuccess { hero ->
                if (shazam.claimDailyReward()) reward(hero)
                findNavController().navigate(
                    R.id.characterDetailFragment,
                    bundleOf("apiDetailUrl" to hero.apiDetailUrl, "characterName" to hero.name)
                )
            }
            .onFailure { toast(it.message.orEmpty()) }
    }
}

/** Figurinha dele se estiver no álbum; senão um pacote Básico. */
private suspend fun Fragment.reward(hero: CharacterSummary) {
    val store = StickerStore(requireContext())
    val inAlbum = ComicVineRepository().popularCharacters().getOrNull().orEmpty().any { it.id == hero.id }
    if (inAlbum) {
        store.add(listOf(hero.id))
        toast(getString(R.string.shazam_sticker, hero.name))
    } else {
        store.addPack(PackType.BASIC)
        toast(getString(R.string.shazam_pack))
    }
}

private fun Fragment.toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
