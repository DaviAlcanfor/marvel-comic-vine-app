package com.projeto.marvel

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import androidx.appcompat.app.AppCompatDelegate
import com.projeto.marvel.data.remote.ApiClient

class MarvelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        // Sem google-services.json o Firebase não sobe (o app funciona sem login/chat): nada a fazer.
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckFactory())
        }
    }
}
