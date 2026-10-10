package com.jake.duolauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import java.net.URLEncoder

internal enum class HandoffKind { WEB, STORE }

/** A store that can be asked to search. [id] is what is saved in settings. */
internal data class StoreInfo(val id: String, val label: String, val packageName: String)

/** A ready-to-show suggestion: which installed app will be opened, and how it is labelled. */
internal data class HandoffTarget(val kind: HandoffKind, val packageName: String, val appLabel: String, val icon: Bitmap?) {
    val title: String get() = if (kind == HandoffKind.WEB) "Search the web with $appLabel" else "Search $appLabel"
}

/** An Android intent described without Android types, so what we send is unit-tested. */
internal data class HandoffIntent(val action: String, val dataUri: String?, val searchQuery: String?)

/** Hands a search to another app. Uno never contacts anything itself: it only asks an app the user already has (a
 * browser, F-Droid, Aurora Store) to run the search, and which app is the user's choice. The web search carries only
 * the words, so the browser's own search engine decides where it goes: no engine address is built into Uno.
 */
internal object HandoffLogic {
    val KNOWN_STORES = listOf(
        StoreInfo("fdroid", "F-Droid", "org.fdroid.fdroid"),
        StoreInfo("aurora", "Aurora Store", "com.aurora.store"),
        StoreInfo("play", "Google Play", "com.android.vending"),
    )
    const val VANADIUM = "app.vanadium.browser"
    val DEFAULT_STORES = setOf("fdroid", "aurora")
    const val MAX_QUERY = 200

    /** Suggestions only appear when nothing installed matched and nothing already answered the query, so they never
     * crowd out a real result.
     */
    fun offerStores(query: String, appMatches: Int, hasAnswer: Boolean) = query.trim().length >= 3 && appMatches == 0 && !hasAnswer
    fun offerWeb(query: String, appMatches: Int, hasAnswer: Boolean) = query.trim().length >= 2 && appMatches == 0 && !hasAnswer

    /** The browser to use: the one chosen in settings if it is still installed, else Vanadium, else any installed browser. */
    fun pickBrowser(chosen: String?, installed: List<String>): String? =
        chosen?.takeIf { it in installed } ?: VANADIUM.takeIf { it in installed } ?: installed.firstOrNull()

    /** Stores to offer: those enabled in settings that are actually installed, in the order of [KNOWN_STORES]. */
    fun pickStores(enabledIds: Set<String>, installedPackages: Set<String>): List<StoreInfo> =
        KNOWN_STORES.filter { it.id in enabledIds && it.packageName in installedPackages }

    fun clean(query: String): String = query.filter { !it.isISOControl() }.trim().take(MAX_QUERY)

    /** Every store handles the `market://search` scheme. */
    fun storeIntent(query: String) = HandoffIntent(Intent.ACTION_VIEW,
        "market://search?q=" + URLEncoder.encode(clean(query), "UTF-8").replace("+", "%20"), null)

    fun webIntent(query: String) = HandoffIntent(Intent.ACTION_WEB_SEARCH, null, clean(query))
}

/** Finds what is installed and opens it. Built on demand from the package manager (see the manifest's <queries>). */
internal class HandoffResolver(private val context: Context, private val extras: ExtrasState) {
    private val pm: PackageManager = context.packageManager

    private fun installed(pkg: String) = runCatching { pm.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false)

    private fun browsers(): List<String> = runCatching {
        pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE), 0)
            .map { it.activityInfo.packageName }.distinct()
    }.getOrDefault(emptyList())

    /** Stores always show their well-known name (some devices label the Play Store package oddly); a browser shows its own. */
    private fun target(kind: HandoffKind, pkg: String, label: String?, fallbackLabel: String) = HandoffTarget(kind, pkg,
        label ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(fallbackLabel),
        runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull())

    /** The rows to show for [query], given how many installed apps matched and whether an answer already appeared. */
    fun targets(query: String, appMatches: Int, hasAnswer: Boolean): List<HandoffTarget> {
        if (!extras.handoff) return emptyList()
        val out = mutableListOf<HandoffTarget>()
        if (HandoffLogic.offerStores(query, appMatches, hasAnswer)) {
            HandoffLogic.pickStores(extras.handoffStores, HandoffLogic.KNOWN_STORES.map { it.packageName }.filter(::installed).toSet())
                .forEach { out += target(HandoffKind.STORE, it.packageName, it.label, it.label) }
        }
        if (HandoffLogic.offerWeb(query, appMatches, hasAnswer)) {
            HandoffLogic.pickBrowser(extras.webPackage, browsers())?.let { out += target(HandoffKind.WEB, it, null, "your browser") }
        }
        return out
    }

    fun open(target: HandoffTarget, query: String) {
        val spec = if (target.kind == HandoffKind.WEB) HandoffLogic.webIntent(query) else HandoffLogic.storeIntent(query)
        val intent = Intent(spec.action).setPackage(target.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        spec.dataUri?.let { intent.data = Uri.parse(it) }
        spec.searchQuery?.let { intent.putExtra(android.app.SearchManager.QUERY, it) }
        // Check before launching: Android can quietly drop a request nothing handles, which would look like a dead button.
        val handled = intent.resolveActivity(pm) != null
        when {
            handled -> runCatching { context.startActivity(intent) }
            // Some browsers don't take a web-search request: ask whichever app handles web searches instead.
            target.kind == HandoffKind.WEB -> runCatching { context.startActivity(Intent(intent).setPackage(null)) }
                .onFailure { android.widget.Toast.makeText(context, "No app can run a web search.", android.widget.Toast.LENGTH_SHORT).show() }
            else -> android.widget.Toast.makeText(context, "${target.appLabel} can't open searches on this device.",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** Runs [query] as a web search in the chosen browser (Vanadium if it is installed), with the words already in the search box of the
     * results page. This is what pressing Enter in a search field does, whether or not the "suggest other apps and the web" rows are on.
     */
    fun searchWeb(query: String): Boolean {
        val words = HandoffLogic.clean(query)
        if (words.isEmpty()) return false
        val pkg = HandoffLogic.pickBrowser(extras.webPackage, browsers())
        if (pkg == null) {
            android.widget.Toast.makeText(context, "No browser is installed to search with.", android.widget.Toast.LENGTH_SHORT).show()
            return false
        }
        open(target(HandoffKind.WEB, pkg, null, "your browser"), words)
        return true
    }

    fun installedBrowsers(): List<Pair<String, String>> = browsers().map { pkg ->
        pkg to runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    fun isStoreInstalled(store: StoreInfo) = installed(store.packageName)
}

@Composable
internal fun rememberHandoffResolver(extras: ExtrasState): HandoffResolver {
    val context = LocalContext.current
    return remember(context, extras.handoff, extras.webPackage, extras.handoffStores) { HandoffResolver(context, extras) }
}
