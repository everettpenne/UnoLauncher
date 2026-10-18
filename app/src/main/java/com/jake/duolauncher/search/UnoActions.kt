package com.jake.duolauncher.search

import com.jake.duolauncher.*
import com.jake.duolauncher.island.*
import com.jake.duolauncher.widgets.*
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** What a contact action does: show the dialer, or open the messages app. */
internal enum class ContactActionKind { DIAL, TEXT }

/** A contact with a phone number, ready to dial or text. */
internal data class ContactPhone(val name: String, val number: String)

/** An Android intent described without Android types, so the pure parts are unit-tested. */
internal data class ActionIntent(
    val action: String,
    val dataUri: String? = null,
    val hour: Int? = null,
    val minute: Int? = null,
)

/** The pure parts of running a typed action: the intents to build and the alarm arithmetic. */
internal object ActionLogic {
    // The action strings are Android's Intent.ACTION_* / AlarmClock constants, written out so this stays testable without Android.
    const val ACTION_DIAL = "android.intent.action.DIAL"
    const val ACTION_SENDTO = "android.intent.action.SENDTO"
    const val ACTION_SET_ALARM = "android.intent.action.SET_ALARM"
    const val ACTION_VIEW = "android.intent.action.VIEW"

    /** Only the characters a tel: or smsto: URI understands, so a number stored with spaces or dashes still dials. */
    fun phoneNumber(raw: String): String = raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    fun dialIntent(number: String) = ActionIntent(ACTION_DIAL, "tel:" + phoneNumber(number))
    fun smsIntent(number: String) = ActionIntent(ACTION_SENDTO, "smsto:" + phoneNumber(number))
    fun alarmIntent(hour: Int, minute: Int) = ActionIntent(ACTION_SET_ALARM, null, hour, minute)
    fun viewIntent(url: String) = ActionIntent(ACTION_VIEW, url)

    /** The alarm clock time for "in [minutes] minutes" from [nowHour]:[nowMinute], wrapped past midnight. */
    fun alarmAt(minutes: Int, nowHour: Int, nowMinute: Int): Pair<Int, Int> {
        val total = Math.floorMod(nowHour * 60 + nowMinute + minutes, 24 * 60)
        return (total / 60) to (total % 60)
    }
}

/** Finds contacts with a phone number for "call <name>" and "text <name>". Only while the user has turned
 * contact search on and granted Android's contacts permission; reads names and numbers, caches nothing.
 */
internal object ContactActions {
    fun search(context: Context, query: String, limit: Int = ContactMatch.MAX_RESULTS): List<ContactPhone> {
        val q = query.trim()
        if (q.length < ContactMatch.MIN_QUERY) return emptyList()
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val first = q.substringBefore(' ')
        val found = mutableListOf<ContactPhone>()
        val seen = HashSet<Long>()
        runCatching {
            context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$first%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC")?.use { cursor ->
                while (cursor.moveToNext() && found.size < limit) {
                    val id = cursor.getLong(0)
                    if (!seen.add(id)) continue
                    val name = cursor.getString(1) ?: continue
                    val number = cursor.getString(2) ?: continue
                    if (ContactMatch.matches(name, q)) found += ContactPhone(name, number)
                }
            }
        }
        return found
    }
}

/** Runs a typed action. Everything goes through the launcher's own machinery (the island's timer, stopwatch and
 * torch, Focus, the Note widget) or a standard Android intent handed to another app; nothing is sent anywhere.
 */
internal class ActionResolver(
    private val context: Context,
    private val noteSlot: Int?,
) {
    /** Runs [action]. Focus needs the current state to know whether "focus on"/"focus off" is already done. */
    fun run(action: QuickAction, focusOn: Boolean, toggleFocus: () -> Unit) {
        when (action) {
            is QuickAction.Timer -> IslandTools.startTimer(context, action.seconds * 1000L)
            is QuickAction.Stopwatch -> IslandTools.toggleStopwatch(context)
            is QuickAction.Note -> noteSlot?.let { WidgetData.set(it, "note", WidgetText.note(action.text)) }
            is QuickAction.Alarm -> openAlarm(action.target)
            is QuickAction.Torch -> IslandTools.toggleTorch(context)
            is QuickAction.Focus -> {
                val on = action.desired ?: !focusOn
                if (on != focusOn) toggleFocus()
            }
            is QuickAction.Call, is QuickAction.Text -> Unit
            is QuickAction.Dial -> openContact(ContactActionKind.DIAL, action.number)
            is QuickAction.OpenLink -> open(ActionLogic.viewIntent(action.url))
        }
    }

    private fun openAlarm(target: AlarmTarget) {
        val now = java.time.LocalTime.now()
        val (hour, minute) = when (target) {
            is AlarmTarget.At -> target.hour to target.minute
            is AlarmTarget.In -> ActionLogic.alarmAt(target.minutes, now.hour, now.minute)
        }
        open(ActionLogic.alarmIntent(hour, minute))
    }

    /** Starts [spec] if some installed app can handle it, saying so otherwise (so a tap never looks like a dead button). */
    fun open(spec: ActionIntent) {
        val intent = Intent(spec.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        spec.dataUri?.let { intent.data = Uri.parse(it) }
        spec.hour?.let { intent.putExtra(AlarmClock.EXTRA_HOUR, it) }
        spec.minute?.let { intent.putExtra(AlarmClock.EXTRA_MINUTES, it) }
        if (intent.resolveActivity(context.packageManager) == null) {
            android.widget.Toast.makeText(context, "No app on this device can do this.",
                android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { context.startActivity(intent) }
    }

    /** Dials (shows the dialer, never places the call) or texts a matched contact. */
    fun openContact(kind: ContactActionKind, number: String) {
        open(if (kind == ContactActionKind.DIAL) ActionLogic.dialIntent(number) else ActionLogic.smsIntent(number))
    }
}

@Composable
internal fun rememberActionResolver(noteSlot: Int?): ActionResolver {
    val context = LocalContext.current
    return remember(context, noteSlot) { ActionResolver(context, noteSlot) }
}
