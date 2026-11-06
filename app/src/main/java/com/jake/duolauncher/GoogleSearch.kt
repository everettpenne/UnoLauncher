package com.jake.duolauncher

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle

/** The Google app, which takes the launcher's search when the person chooses Google search. */
internal const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"

/** Public search entry point; no query is submitted and no private Google component is named. */
internal fun googleSearchIntent() = Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
    .setPackage(GOOGLE_PACKAGE)
    .putExtra(SearchManager.QUERY, "")
    .putExtra(SearchManager.APP_DATA, Bundle().apply { putString("source", "launcher-search") })

