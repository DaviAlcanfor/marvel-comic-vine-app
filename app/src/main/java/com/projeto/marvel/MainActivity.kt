package com.projeto.marvel

import android.Manifest
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.request.ImageRequest
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.ReadingStore
import com.projeto.marvel.data.ThemeStore
import com.projeto.marvel.ui.opening.OpeningScript
import com.projeto.marvel.ui.opening.startOpening
import com.projeto.marvel.ui.home.heroOfTheDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import android.view.View
import android.view.animation.OvershootInterpolator
import com.projeto.marvel.ui.Era
import com.projeto.marvel.ui.era
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.projeto.marvel.databinding.ActivityMainBinding
import com.projeto.marvel.ui.widget.scheduleHeroNotification

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Negar só desliga o aviso diário do herói; o resto do app não depende disso.
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // A splash do sistema (só o fundo) sai na hora; a abertura é a tela de HQ animada, que fica
        // até os dados principais chegarem (entre [LOADING_MIN_MILLIS] e [LOADING_MAX_MILLIS]):
        // a Início e o Álbum já abrem prontos, sem esqueleto de carregamento.
        installSplashScreen()
        ThemeStore(this).get().overlay?.let { theme.applyStyle(it, true) }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            val themes = ThemeStore(this)
            val script = themes.opening()?.let { name -> OpeningScript.entries.firstOrNull { it.name == name } }
            val hero = ReadingStore(this, AuthRepository().currentUser?.uid).preferences().hero
            val finish = binding.startOpening(hero, script ?: OpeningScript.AUTO)
            lifecycleScope.launch {
                val start = System.currentTimeMillis()
                withTimeoutOrNull(LOADING_MAX_MILLIS) { preload() }
                delay((LOADING_MIN_MILLIS - (System.currentTimeMillis() - start)).coerceAtLeast(0))
                finish()
            }
        }
        scheduleHeroNotification(this)
        if (savedInstanceState == null) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            // Com edge-to-edge o teclado não encolhe a tela sozinho: somar o IME mantém o campo do
            // chat (e qualquer campo no rodapé) acima do teclado.
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            // Consumido aqui: sem isso a barra inferior somaria o inset de novo (espaço dobrado).
            WindowInsetsCompat.CONSUMED
        }

        val navController = binding.navHostFragment.getFragment<NavHostFragment>().navController
        binding.bottomNav.setupWithNavController(navController)
        // A barra só aparece nas 5 telas de topo (os ids do menu); some no login, detalhe, arena e
        // Perfil (que abre pelo avatar da Início, a barra só cabe 5 abas).
        // Seleções abertas de outra aba (trilha de um time, herói preferido do Perfil) não são a aba
        // em si: sem barra, senão ela marcaria a aba errada.
        val tabs = setOf(
            R.id.homeFragment,
            R.id.discoverFragment,
            R.id.albumFragment,
            R.id.gamesFragment,
            R.id.battleSelectFragment
        )
        navController.addOnDestinationChangedListener { _, destination, args ->
            val picking = args?.getString("teamUrl") != null || args?.getBoolean("pickHero") == true
            val isTab = destination.id in tabs && !picking
            binding.bottomNav.visibility = if (isTab) View.VISIBLE else View.GONE
            if (isTab) binding.bottomNav.findViewById<View>(destination.id)?.hop(era())
            if (isTab) binding.geekFab.show() else binding.geekFab.hide()
        }
        binding.geekFab.setOnClickListener { navController.navigate(R.id.chatFragment) }
        // Rolando, a bolha sai da frente (cobria a última figurinha da fileira e as missões);
        // parou de rolar, ela volta.
        binding.root.viewTreeObserver.addOnScrollChangedListener {
            if (!geekWanted || binding.bottomNav.visibility != View.VISIBLE) return@addOnScrollChangedListener
            binding.geekFab.hide()
            binding.root.removeCallbacks(showGeek)
            binding.root.postDelayed(showGeek, GEEK_RETURN_MILLIS)
        }
    }

    /** Item da barra que acabou de ser escolhido "pula" no movimento da época. */
    private fun View.hop(era: Era) {
        animate().cancel()
        when (era) {
            Era.RETRO -> {
                rotation = -HOP_TILT
                scaleX = HOP_SCALE
                scaleY = HOP_SCALE
            }
            Era.NINETIES -> translationY = -HOP_LIFT * resources.displayMetrics.density
            Era.MODERN -> {
                scaleX = HOP_SCALE
                scaleY = HOP_SCALE
            }
        }
        animate().rotation(0f).scaleX(1f).scaleY(1f).translationY(0f).setStartDelay(0)
            .setDuration(HOP_MILLIS).setInterpolator(OvershootInterpolator(HOP_TENSION))
    }

    private var geekWanted = true
    private val showGeek = Runnable { setGeekVisible(geekWanted) }

    /** Populares (pool da Início, do Álbum e da Batalha) e a foto do herói do dia, já no cache. */
    private suspend fun preload() {
        val popular = ComicVineRepository().popularCharacters().getOrNull().orEmpty()
        val hero = heroOfTheDay(popular, LocalDate.now())?.image?.mediumUrl ?: return
        imageLoader.execute(ImageRequest.Builder(this).data(hero).build())
    }

    /** Telas cheias por cima da aba (carta em 3D, abertura de pacote) escondem a bolha do Geek. */
    fun setGeekVisible(visible: Boolean) {
        geekWanted = visible
        if (visible && binding.bottomNav.visibility == View.VISIBLE) binding.geekFab.show() else binding.geekFab.hide()
    }

    /** Troca de aba como se o usuário tocasse na barra (atalhos da Home); mantém a pilha de cada aba. */
    fun selectTab(destinationId: Int) {
        binding.bottomNav.selectedItemId = destinationId
    }
}

private const val LOADING_MIN_MILLIS = 1_400L
private const val LOADING_MAX_MILLIS = 3_000L
private const val GEEK_RETURN_MILLIS = 700L
private const val HOP_TILT = 10f
private const val HOP_SCALE = 1.18f
private const val HOP_LIFT = 8f
private const val HOP_MILLIS = 380L
private const val HOP_TENSION = 2.5f
