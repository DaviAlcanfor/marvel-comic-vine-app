package com.projeto.marvel.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.WeatherRepository
import com.projeto.marvel.data.weatherHero
import com.projeto.marvel.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Herói do clima na Início: tempo de agora (Open-Meteo) na sua cidade (localização aproximada,
 * pedida só quando você toca no card) vira um herói — trovoada é Thor, calor é Tocha Humana…
 */
class WeatherCard(private val fragment: Fragment, private val askPermission: () -> Unit) {

    private val context get() = fragment.requireContext()

    fun bind(binding: FragmentHomeBinding) {
        binding.weatherCard.setOnClickListener { if (hasPermission()) load(binding) else askPermission() }
        if (hasPermission()) load(binding)
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun load(binding: FragmentHomeBinding) {
        binding.weatherText.setText(R.string.weather_loading)
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val location = lastLocation(context)
            if (location == null) {
                binding.weatherText.setText(R.string.weather_no_location)
                return@launch
            }
            val (code, temperature) = WeatherRepository().current(location.latitude, location.longitude).getOrElse {
                binding.weatherText.setText(R.string.weather_error)
                return@launch
            }
            val pick = weatherHero(code, temperature)
            val city = city(context, location)
            binding.weatherText.text = fragment.getString(
                R.string.weather_line,
                temperature.roundToInt(),
                pick.weather,
                city ?: fragment.getString(R.string.weather_here),
                pick.name
            )
            val hero = ComicVineRepository().searchCharacters(pick.name).getOrNull()
                ?.firstOrNull { it.image?.mediumUrl != null }
            hero?.let { found ->
                binding.weatherImage.load(found.image?.mediumUrl) { crossfade(true) }
                binding.weatherCard.setOnClickListener {
                    fragment.findNavController().navigate(
                        R.id.characterDetailFragment,
                        bundleOf("apiDetailUrl" to found.apiDetailUrl, "characterName" to found.name)
                    )
                }
            }
        }
    }
}

/** Última posição aproximada conhecida (rede ou passiva): basta para a cidade e o tempo. */
@SuppressLint("MissingPermission") // só chamada depois de checar ACCESS_COARSE_LOCATION
private suspend fun lastLocation(context: Context): Location? {
    val manager = context.getSystemService(LocationManager::class.java)
    val known = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .firstNotNullOfOrNull { provider -> runCatching { manager?.getLastKnownLocation(provider) }.getOrNull() }
    return when {
        known != null -> known
        manager == null || !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> null
        else -> suspendCancellableCoroutine { continuation ->
            manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor) {
                continuation.resume(it)
            }
        }
    }
}

/** Nome da cidade pelo Geocoder do Android (grátis, sem chave); null se não souber. */
private suspend fun city(context: Context, location: Location): String? = suspendCancellableCoroutine { continuation ->
    if (!Geocoder.isPresent()) {
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    Geocoder(context, Locale.forLanguageTag("pt-BR")).getFromLocation(
        location.latitude,
        location.longitude,
        1,
        object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                continuation.resume(addresses.firstOrNull()?.let { it.locality ?: it.subAdminArea })
            }

            override fun onError(errorMessage: String?) = continuation.resume(null)
        }
    )
}
