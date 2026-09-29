package com.projeto.marvel.ui.album

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

private const val CARD_RATIO = 7f / 5f

/**
 * Carta em pé no formato de trading card (5:7). A altura segue a largura; se o lugar tiver altura
 * limitada (quadro da página do álbum), a carta encolhe inteira para caber, sem cortar.
 */
class CardFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = MeasureSpec.getSize(widthMeasureSpec)
        var height = (width * CARD_RATIO).toInt()
        val maxHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED && height > maxHeight) {
            height = maxHeight
            width = (height / CARD_RATIO).toInt()
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
    }
}
