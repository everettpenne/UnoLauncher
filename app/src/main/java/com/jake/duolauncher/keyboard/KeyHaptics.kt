package com.jake.duolauncher.keyboard

import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/** What a key press should feel like. iOS gives each kind of key a slightly different, always light, tick; the keyboard adds its own
 * cues for things that happen without a key (a word corrected, a swipe passing over a key, a word taken whole by Delete).
 */
internal enum class HapticKind {
    LETTER, SPACE, DELETE, DELETE_REPEAT, MODIFIER, RETURN, SELECT, CURSOR, PREVIEW,
    /** Delete has started taking whole words. */ DELETE_WORD,
    /** A second tap on Shift locked caps. */ SHIFT_LOCK,
    /** The space bar or a full stop corrected the word: a soft double pulse, so a correction is felt as well as seen. */ AUTOCORRECT,
    /** A swipe crossed into a new key. */ SWIPE_KEY,
    /** A swipe was lifted and became a word. */ SWIPE_DONE,
}

/** How the keys feel overall, chosen in Settings: [SOFT] rounder and quieter, [CRISP] (the default) short and precise, [FIRM] deeper. */
internal enum class HapticFeel { SOFT, CRISP, FIRM }

/** One haptic: a vibrator primitive at a strength from 0 to 1, or (on a motor without primitives) a predefined effect.
 * A second primitive after a short delay (when [secondScale] is above zero) turns one tick into a soft "toggle" feel.
 */
internal data class HapticPlan(
    val primitive: Int, val scale: Float, val fallbackEffect: Int,
    val secondPrimitive: Int = -1, val secondScale: Float = 0f, val secondDelayMs: Int = 0,
)

/** The feel of each key, with no Android calls beyond constants, so it is unit-tested. */
internal object HapticProfile {
    const val DEFAULT_STRENGTH = .6f
    private const val MIN_AUDIBLE = .06f
    // android.os.VibrationEffect.Composition primitive ids and VibrationEffect predefined ids (inlined constants).
    internal const val CLICK = 1; internal const val THUD = 2; internal const val QUICK_FALL = 6; internal const val TICK = 7; internal const val LOW_TICK = 8
    private const val EFFECT_TICK = 2; private const val EFFECT_CLICK = 0

    /** Letters are the lightest, since they are tapped most. Delete is a shade sharper, its held repeat lighter again and
     * faster (the cadence is in the key's repeat loop); space and Return are a marginally deeper click; shift, page and
     * symbol-layer switches feel like a soft toggle; a popup (the accent strip) pops very softly. */
    private fun crisp(kind: HapticKind): HapticPlan = when (kind) {
        HapticKind.LETTER -> HapticPlan(LOW_TICK, .55f, EFFECT_TICK)
        HapticKind.DELETE -> HapticPlan(TICK, .60f, EFFECT_TICK)
        HapticKind.DELETE_REPEAT -> HapticPlan(LOW_TICK, .48f, EFFECT_TICK)
        HapticKind.DELETE_WORD -> HapticPlan(TICK, .70f, EFFECT_TICK)
        HapticKind.SPACE -> HapticPlan(CLICK, .58f, EFFECT_CLICK)
        HapticKind.RETURN -> HapticPlan(CLICK, .64f, EFFECT_CLICK)
        HapticKind.MODIFIER -> HapticPlan(TICK, .62f, EFFECT_TICK, secondPrimitive = TICK, secondScale = .50f, secondDelayMs = 18)
        HapticKind.SHIFT_LOCK -> HapticPlan(CLICK, .62f, EFFECT_CLICK, secondPrimitive = CLICK, secondScale = .62f, secondDelayMs = 40)
        HapticKind.SELECT -> HapticPlan(TICK, .60f, EFFECT_TICK)
        HapticKind.CURSOR -> HapticPlan(LOW_TICK, .35f, EFFECT_TICK)
        HapticKind.PREVIEW -> HapticPlan(LOW_TICK, .42f, EFFECT_TICK)
        HapticKind.AUTOCORRECT -> HapticPlan(LOW_TICK, .50f, EFFECT_TICK, secondPrimitive = TICK, secondScale = .45f, secondDelayMs = 55)
        HapticKind.SWIPE_KEY -> HapticPlan(LOW_TICK, .28f, EFFECT_TICK)
        HapticKind.SWIPE_DONE -> HapticPlan(CLICK, .58f, EFFECT_CLICK)
    }

    /** The plan for [kind] at [strength] (0 is off; the default setting reproduces the base feel) and [feel], or null for silence. */
    fun plan(kind: HapticKind, strength: Float, feel: HapticFeel = HapticFeel.CRISP): HapticPlan? {
        if (strength <= 0.01f) return null
        val b = when (feel) {
            HapticFeel.CRISP -> crisp(kind)
            // Soft: every tick becomes the rounder low tick, a little quieter; clicks stay clicks but gentler.
            HapticFeel.SOFT -> crisp(kind).let { p -> p.copy(primitive = if (p.primitive == TICK) LOW_TICK else p.primitive, scale = p.scale * .85f,
                secondPrimitive = if (p.secondPrimitive == TICK) LOW_TICK else p.secondPrimitive, secondScale = p.secondScale * .85f) }
            // Firm: ticks become clicks and Return and space land with a short thud underneath.
            HapticFeel.FIRM -> crisp(kind).let { p -> when (kind) {
                HapticKind.SPACE, HapticKind.RETURN -> p.copy(scale = (p.scale * 1.1f), secondPrimitive = THUD, secondScale = .35f, secondDelayMs = 0)
                HapticKind.CURSOR, HapticKind.SWIPE_KEY, HapticKind.DELETE_REPEAT -> p.copy(primitive = TICK, scale = p.scale * 1.1f)
                else -> p.copy(primitive = if (p.primitive == LOW_TICK) TICK else p.primitive, scale = p.scale * 1.1f)
            } }
        }
        val s = (b.scale * strength / DEFAULT_STRENGTH).coerceAtMost(1f)
        if (s < MIN_AUDIBLE) return null
        val s2 = (b.secondScale * strength / DEFAULT_STRENGTH).coerceAtMost(1f)
        return b.copy(scale = s, secondScale = s2)
    }

    /** Fast typing can overlap two fingers; two vibrations within [MIN_GAP_NS] blur into one, so the second is skipped. */
    const val MIN_GAP_NS = 12_000_000L
    fun allow(lastNs: Long, nowNs: Long) = nowNs - lastNs >= MIN_GAP_NS
}

/** Plays key haptics. The vibrator call is a round trip to a system service, so it happens on its own thread and a
 * key press never waits for it. Honours Android's "touch feedback" (haptic feedback) setting.
 */
internal object KeyHaptics {
    @Volatile private var strength = HapticProfile.DEFAULT_STRENGTH
    @Volatile var feel = HapticFeel.CRISP
    /** The primitives this phone's motor plays; a plan using any other falls back to a click. */
    private var supported = emptySet<Int>()
    @Volatile private var systemAllows = true
    private var vibrator: Vibrator? = null
    private var primitives = false
    private var handler: Handler? = null
    private var lastNs = 0L

    fun configure(context: Context, strengthSetting: Float, feelSetting: Int = feel.ordinal) {
        strength = strengthSetting
        feel = HapticFeel.entries.getOrElse(feelSetting) { HapticFeel.CRISP }
        systemAllows = runCatching {
            // Deprecated for apps, with no replacement that says whether the person turned touch feedback off; still honoured if set.
            @Suppress("DEPRECATION") Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        }.getOrDefault(true)
        if (vibrator == null) {
            val v = context.applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
            vibrator = v
            primitives = v != null && runCatching {
                v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    VibrationEffect.Composition.PRIMITIVE_CLICK)
            }.getOrDefault(false)
            supported = if (!primitives) emptySet() else listOf(HapticProfile.CLICK, HapticProfile.THUD, HapticProfile.QUICK_FALL, HapticProfile.TICK,
                HapticProfile.LOW_TICK).filter { id -> runCatching { v!!.areAllPrimitivesSupported(id) }.getOrDefault(false) }.toSet()
            handler = Handler(HandlerThread("uno-key-haptics").apply { start() }.looper)
        }
    }

    // WrongConstant: the plans carry HapticProfile's inlined primitive ids (TICK/LOW_TICK/CLICK), which lint cannot
    // prove are Composition constants; they are the same three ids validated by areAllPrimitivesSupported in configure().
    @android.annotation.SuppressLint("WrongConstant")
    fun fire(kind: HapticKind) {
        if (!systemAllows) return
        val plan = HapticProfile.plan(kind, strength, feel) ?: return
        val now = System.nanoTime()
        if (!HapticProfile.allow(lastNs, now)) return
        lastNs = now
        handler?.post {
            val v = vibrator ?: return@post
            runCatching {
                val effect = if (primitives) {
                    fun usable(id: Int) = if (id in supported) id else HapticProfile.CLICK
                    val composition = VibrationEffect.startComposition().addPrimitive(usable(plan.primitive), plan.scale)
                    // A THUD the motor cannot play is left out rather than turned into a second click.
                    if (plan.secondScale > 0f && (plan.secondPrimitive != HapticProfile.THUD || HapticProfile.THUD in supported))
                        composition.addPrimitive(usable(plan.secondPrimitive), plan.secondScale, plan.secondDelayMs)
                    composition.compose()
                } else VibrationEffect.createPredefined(plan.fallbackEffect)
                if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
                else v.vibrate(effect)
            }
        }
    }
}
