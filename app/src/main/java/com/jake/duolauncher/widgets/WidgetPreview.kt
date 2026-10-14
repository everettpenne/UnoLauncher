package com.jake.duolauncher.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.staticCompositionLocalOf
import java.time.LocalDate

/**
 * The widget picker draws each Uno widget's real face as its preview, so what you pick is what you get. A preview is the same
 * composable the Home screen uses, with two differences: [LocalWidgetPreview] is true (so it ignores taps and shows sample
 * data in place of anything private or empty), and its slot is one of the reserved preview slots below, whose data is never
 * saved ([WidgetData] answers from [PreviewSamples] and ignores writes to them).
 */
internal val LocalWidgetPreview = staticCompositionLocalOf { false }

internal object PreviewSamples {
    /** Preview slots are this base plus the widget's id, far below any real Home slot. */
    const val SLOT_BASE = -100_000

    fun slotFor(id: Int) = SLOT_BASE + id
    fun isPreview(slot: Int) = slot <= SLOT_BASE + 1_000

    /** What the typed widgets show in their preview. */
    fun get(slot: Int, field: String): String? = when (slot - SLOT_BASE to field) {
        UnoWidgets.NOTE to "note" -> "Buy oat milk\nBook the train to Lisbon\nCall Sam about Friday"
        UnoWidgets.COUNTER to "name" -> "Coffees"
        UnoWidgets.COUNTER to "count" -> "3"
        UnoWidgets.COUNTDOWN to "name" -> "Trip to Lisbon"
        UnoWidgets.COUNTDOWN to "date" -> LocalDate.now().plusDays(23).toString()
        UnoWidgets.WORLD_CLOCK to "zones" -> "Europe/London,Asia/Tokyo,America/New_York"
        UnoWidgets.CHECKLIST to "items" -> "1|Buy oat milk\n0|Book the train to Lisbon\n0|Call Sam about Friday"
        else -> null
    }

    /** A place for the sun and moon previews (New York), used instead of the person's own. */
    const val SAMPLE_LATITUDE = 40.7128
    const val SAMPLE_LONGITUDE = -74.0060

    /** A picture for the Photos preview: a warm dusk gradient with a low sun. */
    fun photo(): Bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).also { bmp ->
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, 320f, intArrayOf(0xFF1B2A5C.toInt(), 0xFF7A3F7D.toInt(), 0xFFF2985B.toInt(), 0xFFFFD9A0.toInt()),
            floatArrayOf(0f, .45f, .78f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 320f, 320f, p)
        p.shader = null; p.color = 0xFFFFF1D0.toInt(); c.drawCircle(210f, 224f, 46f, p)
        p.color = 0xFF2A1B3D.toInt(); c.drawCircle(60f, 400f, 200f, p); c.drawCircle(260f, 420f, 190f, p)
    }

    /** A track for the Media player preview. */
    fun artwork(): Bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).also { bmp ->
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 300f, 300f, intArrayOf(0xFFFF5E3A.toInt(), 0xFFB81D6B.toInt(), 0xFF2A1B6B.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 300f, 300f, p)
        p.shader = null; p.color = 0x66FFFFFF; c.drawCircle(210f, 90f, 70f, p); p.color = 0x33000000; c.drawCircle(80f, 230f, 110f, p)
    }
}
