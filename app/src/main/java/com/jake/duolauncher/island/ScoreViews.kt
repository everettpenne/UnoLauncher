package com.jake.duolauncher.island

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap

/**
 * Reading a score off a notification the sports app drew itself. Such a card (theScore's live game is one) has no title and no text:
 * the teams, the scores and the clock are words in the app's own layout, with each team's badge beside them. The card is laid out
 * here exactly as the notification shade lays it out, never shown, and the words on it are handed to [SportsLogic.fromWords]. When
 * they are a score, the two pictures on the card are kept as the teams' badges. Nothing is stored: what was read lasts as long as the
 * notification does.
 */
internal object ScoreViews {
    class Read(val score: SportsScore, val left: Bitmap?, val right: Bitmap?)

    /** The last reading of each notification, by its key, with the post it was read from: a card is laid out once per post. */
    private val read = HashMap<String, Pair<Long, Read?>>()

    /** The score on this card, or null when it is not one. Called on the notification listener's own thread. */
    fun of(context: Context, key: String, postTime: Long, card: RemoteViews): Read? {
        read[key]?.let { (at, was) -> if (at == postTime) return was }
        val now = runCatching { lay(context, card) }.getOrNull()
        read[key] = postTime to now
        return now
    }

    /** Forgets the cards whose notifications have gone. */
    fun keep(present: Set<String>) { read.keys.retainAll(present) }

    private fun lay(context: Context, card: RemoteViews): Read? {
        val root = card.apply(context.applicationContext, FrameLayout(context.applicationContext))
        val words = ArrayList<String>(); val pictures = ArrayList<ImageView>()
        var seen = 0
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE || ++seen > MAX_VIEWS) return
            when (v) {
                is TextView -> v.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { words += it }
                is ImageView -> if (v.drawable != null) pictures += v
                is ViewGroup -> for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        walk(root)
        val score = SportsLogic.fromWords(words) ?: return null
        // Two pictures on a scoreboard are the two teams', in the order the teams are. Any other number is not something to guess at.
        fun badge(v: ImageView) = runCatching { v.drawable.toBitmap(BADGE_PX, BADGE_PX) }.getOrNull()
        return if (pictures.size == 2) Read(score, badge(pictures[0]), badge(pictures[1])) else Read(score, null, null)
    }

    private const val MAX_VIEWS = 60
    /** A badge is kept this large: enough for the open island's card, small beside a 2 MB notification. */
    private const val BADGE_PX = 96
}
