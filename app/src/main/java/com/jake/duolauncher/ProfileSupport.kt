package com.jake.duolauncher

import com.jake.duolauncher.folders.*

import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager

/** Persisted app identity. Personal-profile IDs intentionally retain the legacy component string. */
data class ProfileAppIdentity(val component: String, val userSerial: Long?)

data class AppProfile(
    val userSerial: Long,
    val label: String,
    val isPersonal: Boolean,
    val isWork: Boolean,
    val quiet: Boolean,
    val unlocked: Boolean,
    val available: Boolean,
)

private const val PROFILE_APP_PREFIX = "duo-profile:v1:"

fun profileAppId(component: String, userSerial: Long, personalSerial: Long): String {
    require(component.isNotBlank())
    require(userSerial >= 0)
    return if (userSerial == personalSerial) component else "$PROFILE_APP_PREFIX$userSerial:$component"
}

fun parseProfileAppId(id: String): ProfileAppIdentity? {
    if (!id.startsWith(PROFILE_APP_PREFIX)) return id.takeIf(String::isNotBlank)?.let { ProfileAppIdentity(it, null) }
    val separator = id.indexOf(':', PROFILE_APP_PREFIX.length)
    if (separator <= PROFILE_APP_PREFIX.length || separator == id.lastIndex) return null
    val serialText = id.substring(PROFILE_APP_PREFIX.length, separator)
    if (serialText.any { !it.isDigit() }) return null
    val serial = serialText.toLongOrNull()?.takeIf { it >= 0 } ?: return null
    return ProfileAppIdentity(id.substring(separator + 1), serial)
}

/** A successful associated-profile enumeration is authoritative even when a profile is quiet or locked. */
fun removedAssociatedProfileSerials(cachedWorkSerials: Set<Long>, associatedSerials: Set<Long>): Set<Long> =
    cachedWorkSerials - associatedSerials

fun removedAppIds(
    savedIds: Collection<String>,
    availableIds: Set<String>,
    authoritativeProfiles: Set<Long>,
    temporarilyUnavailable: Set<Pair<Long, String>>,
    confirmedRemoved: Set<Pair<Long, String>>,
    personalSerial: Long,
    definitivelyRemovedProfiles: Set<Long> = emptySet(),
): Set<String> = savedIds.filterTo(mutableSetOf()) { id ->
    if (isReservedFolderId(id)) return@filterTo false
    val identity = parseProfileAppId(id) ?: return@filterTo false
    val serial = identity.userSerial ?: personalSerial
    val packageName = identity.component.substringBefore('/').takeIf(String::isNotBlank) ?: return@filterTo false
    serial in definitivelyRemovedProfiles || serial to packageName in confirmedRemoved ||
        (serial in authoritativeProfiles && id !in availableIds && serial to packageName !in temporarilyUnavailable)
}

/** API 35 identifies managed profiles exactly; earlier LauncherApps exposed associated managed profiles. */
fun isSupportedWorkProfile(launcherApps: LauncherApps, user: UserHandle): Boolean =
    Build.VERSION.SDK_INT < 35 || runCatching {
        launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_MANAGED
    }.getOrDefault(false)

/**
 * Which profile a notification came from, for the island and the badges. A launcher's notification listener hears the work profile and
 * Private Space as well as the personal profile, all under the same package names, so without this a work Slack message badged the
 * personal Slack icon and an app installed only in the work profile never got a card at all.
 */
internal object NotificationProfiles {
    /** The badge key: the package for the personal profile, and "serial:package" for any other, matching [AppEntry]'s profile. */
    fun badgeKey(packageName: String, userSerial: Long?): String = if (userSerial == null) packageName else "$userSerial:$packageName"

    /** The name shown on a card or activity: the app's name, and the profile's in brackets when it is not the personal one. */
    fun label(appLabel: String, profile: String?): String = if (profile == null) appLabel else "$appLabel ($profile)"

    /** The profile's short name from Android's user type (API 35+); older versions give a launcher only managed (work) profiles. */
    fun profileName(userType: String?): String = when (userType) {
        UserManager.USER_TYPE_PROFILE_PRIVATE -> "Private"
        UserManager.USER_TYPE_PROFILE_CLONE -> "Clone"
        else -> "Work"
    }
}

/**
 * An app that changes its launcher icon switches which activity-alias is enabled, so its launcher entry (and therefore its id here) changes while
 * the app is the same. Without this the old id is "removed" and the app drops off Home and out of its folder, and has to be placed again.
 * For each saved id that is no longer available, when its package has exactly one launcher entry that nothing saved refers to, that entry is the
 * successor. [candidates] are the live entries as (id, package, user serial). Pure, so it is unit-tested.
 */
fun launcherAliasSuccessors(
    savedIds: Collection<String>,
    availableIds: Set<String>,
    candidates: List<Triple<String, String, Long>>,
    personalSerial: Long,
): Map<String, String> {
    val saved = savedIds.toSet()
    val moves = LinkedHashMap<String, String>()
    val claimed = mutableSetOf<String>()
    for (id in saved) {
        if (id in availableIds || isReservedFolderId(id)) continue
        val identity = parseProfileAppId(id) ?: continue
        val serial = identity.userSerial ?: personalSerial
        val packageName = identity.component.substringBefore('/').takeIf(String::isNotBlank) ?: continue
        val options = candidates.filter { (candidate, pkg, user) -> pkg == packageName && user == serial && candidate !in saved && candidate !in claimed }
        if (options.size == 1) { moves[id] = options[0].first; claimed += options[0].first }
    }
    return moves
}
