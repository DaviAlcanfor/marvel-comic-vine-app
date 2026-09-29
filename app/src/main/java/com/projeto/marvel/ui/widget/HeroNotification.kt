package com.projeto.marvel.ui.widget

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.imageLoader
import coil.request.ImageRequest
import com.projeto.marvel.MainActivity
import com.projeto.marvel.R
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.ui.home.heroOfTheDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private const val CHANNEL_ID = "hero_of_the_day"
private const val NOTIFICATION_ID = 1
private const val IMAGE_SIZE_PX = 480
private const val NOTIFICATION_HOUR = 10

/** Mesmo herói da Início, com a foto já em bitmap comum (widget e notificação não aceitam "hardware"). */
suspend fun loadHeroOfTheDay(context: Context): Pair<CharacterSummary, Bitmap?>? {
    val hero = heroOfTheDay(ComicVineRepository().popularCharacters().getOrNull().orEmpty(), LocalDate.now())
        ?: return null
    val image = hero.image?.mediumUrl?.let { url ->
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(IMAGE_SIZE_PX).build()
        (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
    }
    return hero to image
}

fun openAppIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

/**
 * Agenda o aviso diário (às 10 h) se ainda não estiver agendado. Chamado a cada abertura do app:
 * reagendar sempre empurraria o alarme para frente; e o reboot apaga alarmes, então isso também
 * serve de receiver de boot.
 */
fun scheduleHeroNotification(context: Context) {
    val intent = Intent(context, HeroNotification::class.java)
    val flags = PendingIntent.FLAG_IMMUTABLE
    if (PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_NO_CREATE) != null) return
    val next = LocalDate.now().atTime(NOTIFICATION_HOUR, 0).let {
        if (it.isAfter(LocalDateTime.now())) it else it.plusDays(1)
    }
    context.getSystemService(AlarmManager::class.java).setInexactRepeating(
        AlarmManager.RTC,
        next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        AlarmManager.INTERVAL_DAY,
        PendingIntent.getBroadcast(context, 0, intent, flags)
    )
}

/** Notificação "Herói do dia" com a foto; toque abre o app. Sem permissão, não mostra nada. */
class HeroNotification : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (hero, image) = loadHeroOfTheDay(context) ?: return@launch
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.widget_label),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_mask)
                    .setContentTitle(context.getString(R.string.notification_title, hero.name))
                    .setContentText(hero.deck ?: context.getString(R.string.notification_text))
                    .setLargeIcon(image)
                    .setStyle(image?.let { NotificationCompat.BigPictureStyle().bigPicture(it) })
                    .setContentIntent(openAppIntent(context))
                    .setAutoCancel(true)
                    .build()
                @Suppress("MissingPermission") // conferido em areNotificationsEnabled acima
                manager.notify(NOTIFICATION_ID, notification)
            } finally {
                pending.finish()
            }
        }
    }
}
