package com.projeto.marvel.ui.profile

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.projeto.marvel.R
import com.projeto.marvel.data.ThemeStore
import com.projeto.marvel.databinding.FragmentProfileBinding
import com.projeto.marvel.ui.Era
import com.projeto.marvel.ui.EraPanelDrawable
import com.projeto.marvel.ui.comicDialog
import com.projeto.marvel.ui.era
import com.projeto.marvel.ui.opening.OpeningScript

/** Avatar do Perfil no traço da época, igual ao da Início: quadro de nanquim, moldura neon ou anel vermelho. */
fun FragmentProfileBinding.applyEra() {
    val context = root.context
    when (context.era()) {
        Era.RETRO, Era.NINETIES -> {
            avatar.background = EraPanelDrawable(context, EraPanelDrawable.Kind.PANEL)
            avatar.foreground = null
            val frame = context.resources.getDimensionPixelSize(R.dimen.space_xs)
            avatar.setPadding(frame, frame, frame, frame)
        }
        Era.MODERN -> avatar.foreground = ContextCompat.getDrawable(context, R.drawable.fg_modern_ring)
    }
}

/**
 * Medalha de conquista na cor [fill]: círculo com nanquim (Retrô), placa chanfrada (Anos 90) ou
 * círculo liso (Moderno). Substitui o backgroundTint, que pintava o contorno junto.
 */
fun ImageView.medal(fill: Int) {
    backgroundTintList = null
    background = medalShape(this, fill)
}

private fun medalShape(view: ImageView, fill: Int): Drawable {
    val context = view.context
    return when (context.era()) {
        Era.NINETIES -> EraPanelDrawable(context, EraPanelDrawable.Kind.CHAMFER_FILL, fill)
        Era.RETRO -> GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            val ink = (RETRO_INK_DP * context.resources.displayMetrics.density).toInt()
            setStroke(ink, ContextCompat.getColor(context, R.color.ink))
        }
        Era.MODERN -> GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
        }
    }
}

private const val RETRO_INK_DP = 2.5f

/** Escolha do roteiro da abertura (vale na próxima vez que o app abrir). */
fun Context.pickOpening() {
    val store = ThemeStore(this)
    val labels = listOf(R.string.opening_auto, R.string.opening_cover, R.string.opening_panels, R.string.opening_burst)
        .map(::getString).toTypedArray()
    val current = OpeningScript.entries.indexOfFirst { it.name == store.opening() }.coerceAtLeast(0)
    comicDialog()
        .setTitle(R.string.opening_title)
        .setSingleChoiceItems(labels, current) { dialog, which ->
            store.setOpening(OpeningScript.entries[which].name)
            dialog.dismiss()
        }
        .show()
}
