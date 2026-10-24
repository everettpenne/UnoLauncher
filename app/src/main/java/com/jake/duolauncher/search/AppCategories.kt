package com.jake.duolauncher.search

/**
 * The App Library by category, as iOS groups it: each app under the category it declares in its own manifest (Games, Social, Maps &
 * Navigation...), named by Android. Apps that declare none go under [other], last. Pure, so the order is tested.
 */
internal object AppCategories {
    /** [UNDEFINED] is ApplicationInfo.CATEGORY_UNDEFINED. */
    const val UNDEFINED = -1

    /** Categories with the most apps first (then by name), [other] last; the apps in each by name. */
    fun <T> group(apps: List<T>, categoryOf: (T) -> Int, title: (Int) -> String?, label: (T) -> String, other: String = "Other"): Map<String, List<T>> {
        val named = apps.groupBy { app -> categoryOf(app).takeIf { it != UNDEFINED }?.let(title)?.takeIf(String::isNotBlank) ?: other }
        val order = named.keys.filter { it != other }.sortedWith(compareByDescending<String> { named.getValue(it).size }.thenBy { it }) +
            listOfNotNull(other.takeIf { it in named })
        return order.associateWith { key -> named.getValue(key).sortedBy { label(it).lowercase() } }
    }
}
