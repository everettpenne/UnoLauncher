package com.jake.duolauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

/** The permission ledger: every permission the launcher uses, why, what it can see, and a way to turn it off. */
@Composable
internal fun PermissionLedgerSection(actions: ExtrasActions) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val note = MaterialTheme.typography.bodySmall
    // Re-read when the user comes back from Android's settings, so the ledger shows what they just changed.
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val inputs = remember(refresh, actions.store.state.islandEverywhere) { ledgerInputs(context, actions) }
    Text("Permissions & privacy", style = MaterialTheme.typography.titleMedium)
    Text(PermissionLedger.islandSummary(inputs), style = note, color = muted, modifier = Modifier.testTag("ledger-summary"))
    PermissionLedger.rows(inputs).forEach { row ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("ledger-${row.id}")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(row.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(row.status, color = if (row.on) MaterialTheme.colorScheme.primary else muted,
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("ledger-status-${row.id}"))
            }
            Text("Used for: ${row.usedFor}", style = note, color = muted)
            Text("Can see: ${row.canSee}", style = note, color = muted)
            if (row.change != LedgerAction.NONE) OutlinedButton(onClick = { openLedgerAction(context, row.change) },
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ledger-open-${row.id}")) { Text("Change in Android's settings") }
        }
    }
}

private fun ledgerInputs(context: Context, actions: ExtrasActions): LedgerInputs {
    val service = ComponentName(context, SystemShadeAccessibilityService::class.java).flattenToString()
    val enabledList = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    }.getOrNull().orEmpty()
    return LedgerInputs(
        overlayAllowed = Settings.canDrawOverlays(context),
        accessibilityEnabledInSettings = enabledList.split(':').any { it.equals(service, ignoreCase = true) },
        accessibilityRunning = SystemShadeAccessibilityService.isConnected(),
        notificationAccess = actions.hasNotificationAccess(),
        contactsAllowed = actions.hasContactsPermission(),
        islandEverywhere = actions.store.state.islandEverywhere,
    )
}

private fun openLedgerAction(context: Context, action: LedgerAction) {
    val intent = when (action) {
        LedgerAction.OVERLAY_SETTINGS -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        LedgerAction.ACCESSIBILITY_SETTINGS -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        LedgerAction.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        LedgerAction.APP_INFO -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        LedgerAction.NONE -> return
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
