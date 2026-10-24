package com.jake.duolauncher

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.feed.*
import com.jake.duolauncher.appearance.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun DiscoverContent(modifier: Modifier = Modifier, feed: FeedState = FeedState(),
    feedVisible: Boolean = false, onFeedRefresh: () -> Unit = {},
    onFeedOpenEntry: (String) -> Unit = {}, onAddFeed: () -> Unit = {},
    onFeedToggleSave: (String) -> Unit = {},
    glassBackdrop: com.kyant.backdrop.Backdrop? = null,
    glassTint: Color = Glass.copy(alpha = .82f),
    settings: GlassSettings = GlassSettings.Default) {
    if (feedVisible) {
        FeedPage(feed, onFeedRefresh, onFeedOpenEntry, onAddFeed, onFeedToggleSave, modifier, glassBackdrop, glassTint, settings)
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val message = LiveDiscover.message.value
    val googleIntent = remember(message) {
        context.packageManager.getLaunchIntentForPackage(DiscoverClient.GOOGLE_PACKAGE)
    }
    var showMessage by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        showMessage = false
        if (message != null) { delay(650); showMessage = true }
    }
    Box(modifier.testTag("discover-page")) {
        // The healthy native feed moves above this page. Keep its backing page transparent
        // so the retained Home layer is revealed during entry and exit, not an empty glass card.
        val glass = LocalPageGlass.current
        if (showMessage && message != null) Surface(Modifier.fillMaxSize().testTag("discover-recovery-surface")
            .then(if (glass != null) Modifier.liquidGlass(glass.backdrop, Corner.xlarge,
                glass.tint, kind = GlassKind.REGULAR, settings = glass.settings) else Modifier),
            shape = Corner.xlarge, color = if (glass != null) Color.Transparent else Glass.copy(alpha = .92f),
            border = if (glass != null) null else BorderStroke(1.dp, Color.White.copy(alpha = .5f))) {
            Column(Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(com.jake.duolauncher.R.string.discover), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text(message ?: "", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                GlassButton(onClick = LiveDiscover::retry, Modifier.testTag("discover-retry")) { Text(stringResource(com.jake.duolauncher.R.string.retry)) }
                if (googleIntent != null) GlassButton(onClick = {
                    runCatching { context.startActivity(googleIntent) }
                }, Modifier.testTag("discover-open-google")) { Text(stringResource(com.jake.duolauncher.R.string.open_google)) }
                GlassButton(onClick = { LiveDiscover.onHomeRequest?.invoke() },
                    Modifier.testTag("discover-return-home")) { Text(stringResource(com.jake.duolauncher.R.string.back_to_home_2)) }
            }
        }
    }
}
