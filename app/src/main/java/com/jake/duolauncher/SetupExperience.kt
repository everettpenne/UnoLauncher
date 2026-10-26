package com.jake.duolauncher

import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.Image
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.currentStateAsState
import com.jake.duolauncher.island.StatusBarReaderService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal enum class SetupEntryDecision { SHOW, ALREADY_FINISHED, EXISTING_INSTALL }

internal fun setupEntryDecision(
    finished: Boolean,
    started: Boolean,
    hadLauncherState: Boolean,
): SetupEntryDecision = when {
    finished -> SetupEntryDecision.ALREADY_FINISHED
    started -> SetupEntryDecision.SHOW
    hadLauncherState -> SetupEntryDecision.EXISTING_INSTALL
    else -> SetupEntryDecision.SHOW
}

/** Setup is intentionally separate from launcher state so it cannot rewrite an upgraded layout. */
internal class SetupExperience(context: Context, prefsName: String = PREFS) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun entryDecision(hadLauncherState: Boolean): SetupEntryDecision {
        val decision = setupEntryDecision(
            finished = prefs.getBoolean(FINISHED, false),
            started = prefs.getBoolean(STARTED, false),
            hadLauncherState = hadLauncherState,
        )
        // A fresh launch writes only setup state. This keeps the cohort stable after the
        // launcher creates its normal layout preferences or Android recreates the activity.
        if (decision == SetupEntryDecision.SHOW && !prefs.getBoolean(STARTED, false))
            prefs.edit().putBoolean(STARTED, true).commit()
        return decision
    }

    fun finish() {
        // Finish before dismissing the sheet so process death cannot make it reappear.
        prefs.edit().putBoolean(FINISHED, true).commit()
    }

    companion object {
        private const val PREFS = "setup_experience"
        private const val FINISHED = "finished"
        private const val STARTED = "started"

        fun hadLauncherState(context: Context): Boolean =
            context.getSharedPreferences("launcher", Context.MODE_PRIVATE).all.isNotEmpty() ||
                context.getSharedPreferences("app_catalog", Context.MODE_PRIVATE).all.isNotEmpty()
    }
}

private enum class SetupStep { WELCOME, HOME, ISLAND, LOOK, READY }

/**
 * First run, as five short screens like iOS's setup: Welcome (what Uno is), Home app, the island's two optional switches (each with its state, rechecked
 * on return from Android's page), Look (light or dark, card or glass widgets) and Ready. Every screen has one clear button and a way out ("Not now"), so
 * it never traps anyone; the last has Explore Home and Add a widget. It was one long sheet of everything at once.
 */
@Composable
internal fun FirstRunSetupSheet(
    isDefaultHome: Boolean,
    onMakeDefault: () -> Unit,
    onAddWidget: () -> Unit,
    onExplore: () -> Unit,
    onSkip: () -> Unit,
    appearanceMode: com.jake.duolauncher.appearance.AppearanceMode = com.jake.duolauncher.appearance.AppearanceMode.LIGHT,
    onAppearanceMode: (com.jake.duolauncher.appearance.AppearanceMode) -> Unit = {},
    widgetStyle: WidgetStyle = WidgetStyle.CARD,
    onWidgetStyle: (WidgetStyle) -> Unit = {},
) {
    var step by rememberSaveable { mutableStateOf(SetupStep.WELCOME) }
    val steps = SetupStep.entries
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * .92f).toDp() }
    val accent = launcherAccent().first
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step != SetupStep.WELCOME) IconButton(onClick = { step = steps[step.ordinal - 1] }, Modifier.testTag("setup-back")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(com.jake.duolauncher.R.string.setup_back))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSkip, Modifier.testTag("setup-close")) { Icon(Icons.Rounded.Close, "Close welcome") }
        }
        // The steps slide, one over the next, in the direction of travel.
        androidx.compose.animation.AnimatedContent(step, Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(min = 430.dp), label = "setup step",
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (androidx.compose.animation.slideInHorizontally { if (forward) it / 4 else -it / 4 } + androidx.compose.animation.fadeIn()) togetherWith
                    (androidx.compose.animation.slideOutHorizontally { if (forward) -it / 4 else it / 4 } + androidx.compose.animation.fadeOut())
            }) { current ->
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                when (current) {
                    SetupStep.WELCOME -> WelcomeStep()
                    SetupStep.HOME -> HomeStep(isDefaultHome)
                    SetupStep.ISLAND -> IslandStep()
                    SetupStep.LOOK -> LookStep(appearanceMode, onAppearanceMode, widgetStyle, onWidgetStyle)
                    SetupStep.READY -> ReadyStep()
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // One prominent button per screen; the way out is plain text under it ("Not now"; on the last screen the prominent button is the way out).
        when (step) {
            SetupStep.HOME -> if (!isDefaultHome) {
                GlassButton(onClick = onMakeDefault, prominent = true, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("setup-make-default")) {
                    Text(stringResource(com.jake.duolauncher.R.string.choose_home_app), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                GlassButton(onClick = { step = steps[step.ordinal + 1] }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("setup-continue")) {
                    Text(stringResource(com.jake.duolauncher.R.string.setup_continue))
                }
            } else SetupContinue { step = steps[step.ordinal + 1] }
            SetupStep.READY -> {
                GlassButton(onClick = onExplore, prominent = true, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("setup-explore")) {
                    Text(stringResource(com.jake.duolauncher.R.string.explore_home), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                GlassButton(onClick = onAddWidget, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("setup-add-widget")) {
                    Text(stringResource(com.jake.duolauncher.R.string.add_a_widget))
                }
            }
            else -> SetupContinue { step = steps[step.ordinal + 1] }
        }
        if (step != SetupStep.READY) {
            Box(Modifier.heightIn(min = 44.dp).clip(Corner.pill).clickable(onClick = onSkip).padding(horizontal = 20.dp).testTag("setup-explore"), contentAlignment = Alignment.Center) {
                Text(stringResource(com.jake.duolauncher.R.string.not_now), color = accent, fontWeight = FontWeight.Medium)
            }
        } else Spacer(Modifier.height(44.dp))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            steps.forEach { dot ->
                Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (dot == step) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = .2f)))
            }
        }
    }
}

@Composable
private fun SetupContinue(onClick: () -> Unit) {
    GlassButton(onClick = onClick, prominent = true, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("setup-continue")) {
        Text(stringResource(com.jake.duolauncher.R.string.setup_continue), fontWeight = FontWeight.SemiBold)
    }
}

/** A large rounded-square glyph above a screen's title, in the accent. */
@Composable
private fun SetupHero(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(84.dp).clip(RoundedCornerShape(24.dp))
        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint.copy(alpha = .95f), tint.copy(alpha = .72f)))),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(46.dp))
    }
}

@Composable
private fun SetupTitle(title: String, detail: String) {
    Text(title, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Text(detail, Modifier.padding(top = 8.dp, bottom = 20.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
private fun WelcomeStep() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember { runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(256, 256).asImageBitmap() }.getOrNull() }
    if (icon != null) Image(icon, null, Modifier.size(92.dp).clip(Corner.icon))
    else SetupHero(Icons.Rounded.Home, launcherAccent().first)
    SetupTitle(stringResource(com.jake.duolauncher.R.string.welcome_to_uno_launcher), stringResource(com.jake.duolauncher.R.string.a_quiet_home_screen_built_for))
    SetupFeature(Icons.Rounded.GridView, androidx.compose.ui.graphics.Color(0xFF0A84FF),
        stringResource(com.jake.duolauncher.R.string.setup_feature_home_title), stringResource(com.jake.duolauncher.R.string.setup_feature_home_detail))
    SetupFeature(Icons.Rounded.Widgets, androidx.compose.ui.graphics.Color(0xFFFF9F0A),
        stringResource(com.jake.duolauncher.R.string.setup_feature_island_title), stringResource(com.jake.duolauncher.R.string.setup_feature_island_detail))
    SetupFeature(Icons.Rounded.Lock, androidx.compose.ui.graphics.Color(0xFF30B0C7),
        stringResource(com.jake.duolauncher.R.string.setup_feature_private_title), stringResource(com.jake.duolauncher.R.string.setup_feature_private_detail))
}

@Composable
private fun HomeStep(isDefaultHome: Boolean) {
    SetupHero(if (isDefaultHome) Icons.Rounded.Check else Icons.Rounded.Home, if (isDefaultHome) androidx.compose.ui.graphics.Color(0xFF30D158) else androidx.compose.ui.graphics.Color(0xFF0A84FF))
    SetupTitle(stringResource(if (isDefaultHome) com.jake.duolauncher.R.string.setup_home_done_title else com.jake.duolauncher.R.string.setup_home_title),
        stringResource(if (isDefaultHome) com.jake.duolauncher.R.string.setup_home_done_detail else com.jake.duolauncher.R.string.setup_home_detail))
}

@Composable
private fun IslandStep() {
    SetupHero(Icons.Rounded.Widgets, androidx.compose.ui.graphics.Color(0xFF1C1C1E))
    SetupTitle(stringResource(com.jake.duolauncher.R.string.setup_island_title), stringResource(com.jake.duolauncher.R.string.setup_island_detail))
    IslandSetupGroup()
}

@Composable
private fun LookStep(mode: com.jake.duolauncher.appearance.AppearanceMode, onMode: (com.jake.duolauncher.appearance.AppearanceMode) -> Unit,
    widgetStyle: WidgetStyle, onWidgetStyle: (WidgetStyle) -> Unit) {
    SetupHero(Icons.Rounded.Palette, androidx.compose.ui.graphics.Color(0xFFBF5AF2))
    SetupTitle(stringResource(com.jake.duolauncher.R.string.setup_look_title), stringResource(com.jake.duolauncher.R.string.setup_look_detail))
    Text(stringResource(com.jake.duolauncher.R.string.setup_look_appearance), Modifier.fillMaxWidth().padding(bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
    val light = stringResource(com.jake.duolauncher.R.string.setup_light); val dark = stringResource(com.jake.duolauncher.R.string.setup_dark)
    val auto = stringResource(com.jake.duolauncher.R.string.setup_automatic)
    GlassChips(listOf(com.jake.duolauncher.appearance.AppearanceMode.LIGHT, com.jake.duolauncher.appearance.AppearanceMode.DARK, com.jake.duolauncher.appearance.AppearanceMode.SYSTEM),
        mode, { when (it) { com.jake.duolauncher.appearance.AppearanceMode.LIGHT -> light; com.jake.duolauncher.appearance.AppearanceMode.DARK -> dark; else -> auto } },
        onMode, Modifier.fillMaxWidth(), tagPrefix = "setup-mode", key = { it.name.lowercase() })
    Text(stringResource(com.jake.duolauncher.R.string.setup_look_widgets), Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
    val card = stringResource(com.jake.duolauncher.R.string.setup_widget_card); val glass = stringResource(com.jake.duolauncher.R.string.setup_widget_glass)
    GlassChips(WidgetStyle.entries, widgetStyle, { if (it == WidgetStyle.CARD) card else glass }, onWidgetStyle, Modifier.fillMaxWidth(),
        tagPrefix = "setup-widgets", key = { it.name.lowercase() })
}

@Composable
private fun ReadyStep() {
    SetupHero(Icons.Rounded.Check, androidx.compose.ui.graphics.Color(0xFF30D158))
    SetupTitle(stringResource(com.jake.duolauncher.R.string.setup_ready_title), stringResource(com.jake.duolauncher.R.string.setup_ready_detail))
    SetupFeature(Icons.Rounded.TouchApp, androidx.compose.ui.graphics.Color(0xFF0A84FF),
        stringResource(com.jake.duolauncher.R.string.setup_tip_hold_title), stringResource(com.jake.duolauncher.R.string.setup_tip_hold_detail))
    SetupFeature(Icons.Rounded.Settings, androidx.compose.ui.graphics.Color(0xFF8E8E93),
        stringResource(com.jake.duolauncher.R.string.setup_tip_swipe_title), stringResource(com.jake.duolauncher.R.string.setup_tip_swipe_detail))
    SetupFeature(Icons.Rounded.Widgets, androidx.compose.ui.graphics.Color(0xFFFF9F0A),
        stringResource(com.jake.duolauncher.R.string.setup_tip_island_title), stringResource(com.jake.duolauncher.R.string.setup_tip_island_detail))
}

/** A row as iOS's setup lists what is new: a small coloured square with a white glyph, a bold line and a grey one. */
@Composable
private fun SetupFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Whether a setup switch is on, rechecked each time the person comes back from Android's settings screen. */
@Composable
private fun rememberSwitchState(check: (Context) -> Boolean): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return androidx.compose.runtime.remember(lifecycle) { check(context) }
}

/** The two optional switches that make the island complete, each with its state and one button to Android's page for it. */
@Composable
private fun IslandSetupGroup() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notifications = rememberSwitchState { androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(it).contains(it.packageName) }
    val alignment = rememberSwitchState { StatusBarReaderService.isEnabled(it) }
    SetupSwitchRow(notifications, com.jake.duolauncher.R.string.setup_notifications_title, com.jake.duolauncher.R.string.setup_notifications_detail, "setup-notifications") {
        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
    SetupSwitchRow(alignment, com.jake.duolauncher.R.string.setup_alignment_title, com.jake.duolauncher.R.string.setup_alignment_detail, "setup-alignment") {
        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        android.widget.Toast.makeText(context, com.jake.duolauncher.R.string.setup_alignment_hint, android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun SetupSwitchRow(on: Boolean, title: Int, detail: Int, tag: String, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.weight(1f).padding(start = 34.dp, end = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (on) Icon(Icons.Rounded.Check, stringResource(com.jake.duolauncher.R.string.setup_is_on), tint = MaterialTheme.colorScheme.primary)
        else GlassButton(onClick = onOpen, modifier = Modifier.heightIn(min = 44.dp)) { Text(stringResource(com.jake.duolauncher.R.string.setup_turn_on)) }
    }
}

