package com.projeto.marvel.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.projeto.marvel.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Widget "Herói do dia" na tela inicial: o mesmo da Início; toque abre o app. */
class HeroWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // goAsync segura o broadcast enquanto a API responde (limite do sistema: ~10 s).
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = render(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun render(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hero)
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppIntent(context))
        val (hero, image) = loadHeroOfTheDay(context) ?: run {
            views.setTextViewText(R.id.widgetName, context.getString(R.string.widget_offline))
            return views
        }
        views.setTextViewText(R.id.widgetName, hero.name)
        image?.let { views.setImageViewBitmap(R.id.widgetImage, it) }
        return views
    }
}
