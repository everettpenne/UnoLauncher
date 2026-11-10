package com.jake.duolauncher.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.everettpenne.unolab.IAnswerSink
import com.everettpenne.unolab.IAssistant
import com.jake.duolauncher.UpdateSecurity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/** Where Uno Lab's assistant is. [OFF]: not bound (Spotlight closed, or the setting off). */
internal enum class AssistantState { OFF, NOT_INSTALLED, NO_MODEL, LOADING, READY, FAILED }

/** What the assistant is asked for: a short streamed reply, or exactly one JSON action object (see [AssistantIntent]). */
internal enum class AskMode(val wire: Int) { ANSWER(0), ACTION(1) }

/** The assistant as Spotlight sees it, so a test can hand it a fake instead of Uno Lab. */
internal interface AssistantClient {
    val state: StateFlow<AssistantState>
    /** Binds and warms the model up (Spotlight opened). Idempotent. */
    fun open()
    /** Unbinds (Spotlight closed). Idempotent. */
    fun close()
    /** Streams the reply in pieces; completes when done, fails with the assistant's reason. Cancelling the collector cancels the answer.
     *  [context] is the phone's facts for this question (search.context.PhoneContext); an Uno Lab too old to take it gets the question alone. */
    fun ask(query: String, mode: AskMode, context: String = ""): Flow<String>
    /** Opens Uno Lab's own screen with [query], to carry on there. */
    fun continueInLab(context: Context, query: String)
}

/** Which Uno Lab Uno trusts: its package, and the SHA-256 of the one certificate it is signed with. Pure, so it is tested. */
internal object LabTrust {
    const val PACKAGE = "com.everettpenne.unolab"
    const val SERVICE = "com.everettpenne.unolab.AssistantService"
    const val ASK_ACTION = "com.everettpenne.unolab.ASK"
    const val CERT_SHA256 = "728c5b877c7773a91880e3dcbc72c59ce05c669c994f9a8011ea170063a7fc9a"

    /** Exactly Lab's certificate: a package of that name signed with anything else (or with an extra signer) is not Uno Lab. */
    fun trusted(signers: Set<String>): Boolean = signers.map { it.lowercase() }.toSet() == setOf(CERT_SHA256)

    /** How long "no model" is not taken as final after warm(): Lab starts loading in the background, so the first state() can still
     *  say no model. Taking that at its word hid Ask Qwen for the whole search on the Pixel. */
    const val NO_MODEL_GRACE_MS = 10_000L
    const val WATCH_MS = 60_000L

    /** Whether to keep asking Lab for its state, [waitedMs] after warm(): while it loads, and through [NO_MODEL_GRACE_MS] of "no model". */
    fun keepWatching(state: AssistantState, waitedMs: Long): Boolean = when (state) {
        AssistantState.LOADING -> waitedMs < WATCH_MS
        AssistantState.NO_MODEL -> waitedMs < NO_MODEL_GRACE_MS
        else -> false
    }

    fun fromWire(state: Int): AssistantState = when (state) {
        0 -> AssistantState.NO_MODEL
        1 -> AssistantState.LOADING
        2 -> AssistantState.READY
        else -> AssistantState.FAILED
    }
}

/**
 * Uno Lab's on-device assistant (Qwen, run in Lab's own process so the model never lives in the launcher's), reached over a bound service.
 * Opt-in (Extras › Local assistant). Before binding, Lab's signing certificate is checked against [LabTrust.CERT_SHA256], so another app
 * using Lab's package name is never sent a query. What is sent: the Spotlight query, only when the Ask row is tapped. Nothing goes over the
 * network; Lab has none for this. Every binder call runs on one background thread, in order, never on the main thread.
 */
internal class LocalAssistant(context: Context) : AssistantClient {
    private val app = context.applicationContext
    private val _state = MutableStateFlow(AssistantState.OFF)
    override val state: StateFlow<AssistantState> = _state.asStateFlow()
    private val service = MutableStateFlow<IAssistant?>(null)
    private var scope: CoroutineScope? = null
    private var bound = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val binderThread = Dispatchers.IO.limitedParallelism(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IAssistant.Stub.asInterface(binder) ?: return
            service.value = svc
            scope?.launch(binderThread) { warmAndWatch(svc) }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            // Lab's process went away; Android rebinds on its own when it comes back.
            service.value = null
            if (bound) _state.value = AssistantState.LOADING
        }
        override fun onBindingDied(name: ComponentName?) {
            service.value = null
            if (bound) { close(); open() }
        }
        override fun onNullBinding(name: ComponentName?) { _state.value = AssistantState.FAILED }
    }

    private suspend fun warmAndWatch(svc: IAssistant) {
        val first = runCatching { svc.warm(); svc.state() }.getOrElse { _state.value = AssistantState.FAILED; return }
        var now = LabTrust.fromWire(first)
        _state.value = now
        // Loading takes a few seconds; watch it until it settles (or stop after a minute and leave it as loading).
        var waited = 0L
        while (LabTrust.keepWatching(now, waited) && service.value === svc) {
            delay(400); waited += 400
            now = runCatching { LabTrust.fromWire(svc.state()) }.getOrDefault(AssistantState.FAILED)
            _state.value = now
        }
    }

    override fun open() {
        if (bound) return
        if (!isAvailable(app)) { _state.value = AssistantState.NOT_INSTALLED; return }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        _state.value = AssistantState.LOADING
        bound = runCatching {
            app.bindService(Intent().setComponent(ComponentName(LabTrust.PACKAGE, LabTrust.SERVICE)), connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) { runCatching { app.unbindService(connection) }; scope?.cancel(); scope = null; _state.value = AssistantState.NOT_INSTALLED }
    }

    override fun close() {
        if (!bound) return
        bound = false
        val svc = service.value
        service.value = null
        scope?.let { s -> if (svc != null) CoroutineScope(binderThread).launch { runCatching { svc.cancel() } }; s.cancel() }
        scope = null
        runCatching { app.unbindService(connection) }
        _state.value = AssistantState.OFF
    }

    /** Uno Lab's interface version, asked once per connection: 0 for a Lab from before askWithContext (the call is unknown there and reads as 0). */
    private var labVersion: Pair<IAssistant, Int>? = null

    override fun ask(query: String, mode: AskMode, context: String): Flow<String> = callbackFlow {
        val svc = withTimeoutOrNull(15_000) { service.filterNotNull().first() } ?: throw AssistantError("Uno Lab isn't connected.")
        val finished = AtomicBoolean(false)
        val sink = object : IAnswerSink.Stub() {
            override fun onPiece(piece: String?) { if (piece != null) trySend(piece) }
            override fun onDone(ms: Long, tokens: Int) { finished.set(true); channel.close() }
            override fun onError(why: String?) { finished.set(true); channel.close(AssistantError(why ?: "Uno Lab couldn't answer.")) }
        }
        try {
            withContext(binderThread) {
                val version = labVersion?.takeIf { it.first === svc }?.second ?: runCatching { svc.version() }.getOrDefault(0).also { labVersion = svc to it }
                if (context.isNotBlank() && version >= 1) svc.askWithContext(query.take(AskPolicy.MAX_QUERY), context, mode.wire, sink)
                else svc.ask(query.take(AskPolicy.MAX_QUERY), mode.wire, sink)
            }
        } catch (e: Exception) {
            throw AssistantError(if (e is SecurityException) "Uno Lab doesn't accept this copy of Uno." else "Uno Lab couldn't be reached.")
        }
        awaitClose {
            // The collector went away mid-answer (the query changed, Spotlight closed): stop the model rather than let it run on.
            if (!finished.get()) CoroutineScope(binderThread).launch { runCatching { svc.cancel() } }
        }
    }.flowOn(Dispatchers.Default)

    override fun continueInLab(context: Context, query: String) = openLab(context, query)

    companion object {
        /** Lab is installed, signed with its own pinned key, and new enough to have the assistant service. */
        @Suppress("DEPRECATION")
        fun isAvailable(context: Context): Boolean = runCatching {
            val pm = context.packageManager
            val info = pm.getPackageInfo(LabTrust.PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
            val signers = info.signingInfo?.apkContentsSigners?.mapTo(mutableSetOf()) { UpdateSecurity.sha256Hex(it.toByteArray()) }.orEmpty()
            LabTrust.trusted(signers) &&
                pm.resolveService(Intent().setComponent(ComponentName(LabTrust.PACKAGE, LabTrust.SERVICE)), 0) != null
        }.getOrDefault(false)

        fun openLab(context: Context, query: String) {
            val intent = Intent(LabTrust.ASK_ACTION).setPackage(LabTrust.PACKAGE)
                .putExtra(Intent.EXTRA_TEXT, query.take(AskPolicy.MAX_QUERY)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}

internal class AssistantError(message: String) : Exception(message)
