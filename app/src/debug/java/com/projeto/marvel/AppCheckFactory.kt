package com.projeto.marvel

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Build de debug: o provedor de depuração imprime no Logcat ("DebugAppCheckProvider") um token
 * que precisa ser cadastrado em Firebase Console → App Check → Apps → Gerenciar tokens de depuração.
 */
fun appCheckFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
