package com.jake.duolauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The unread-count dot on an app icon: iOS red, hanging a little off the icon's top-right corner.
 * Draws nothing for a count of zero, which is every app until the user allows notification access.
 */
@Composable
internal fun AppBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(modifier.offset(x = 5.dp, y = (-5).dp).defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
        .background(Color(0xFFFF3B30), CircleShape).padding(horizontal = 5.dp)
        .semantics { contentDescription = "$count unread" }.testTag("app-badge"),
        contentAlignment = Alignment.Center) {
        Text(BadgeLogic.label(count), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Unread count for [packageName] from the notification listener (always 0 while it is off). */
internal fun badgeCount(packageName: String): Int = NotificationFeed.badges[packageName] ?: 0
