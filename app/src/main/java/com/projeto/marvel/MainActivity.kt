package com.projeto.marvel

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.projeto.marvel.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            // Consumido aqui: sem isso a barra inferior somaria o inset de novo (espaço dobrado).
            WindowInsetsCompat.CONSUMED
        }

        val navController = binding.navHostFragment.getFragment<NavHostFragment>().navController
        binding.bottomNav.setupWithNavController(navController)
        // A barra só aparece nas 3 telas de topo (os ids do menu); some no login, detalhe e arena.
        val tabs = setOf(R.id.homeFragment, R.id.teamsFragment, R.id.battleSelectFragment)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility = if (destination.id in tabs) View.VISIBLE else View.GONE
        }
    }
}
