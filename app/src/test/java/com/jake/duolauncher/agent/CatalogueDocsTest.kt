package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.Catalogue
import com.jake.duolauncher.search.agent.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tools table is generated, and pinned here. Adding or changing a tool fails this test until the expected table is updated, which is the
 * same protection a checked-in doc file would give — without relying on `docs/`, which this repo keeps out of git.
 */
class CatalogueDocsTest {
    @Test fun theGeneratedToolsTableIsStable() {
        val expected = """
            | tool | effect | parameters | does |
            | --- | --- | --- | --- |
            | `timer` | Local | seconds:int, label:text | Starts a countdown timer that the island shows. |
            | `stopwatch` | Local | — | Starts or stops the island's stopwatch. |
            | `torch` | Local | — | Turns the flashlight on or off. |
            | `focus` | Local | on:bool | Turns Focus (Do Not Disturb) on or off; it hides the apps the person picked. |
            | `note` | Local | text:text | Saves a note to the Note widget on Home. |
            | `alarm` | Local | at:text, in_minutes:int | Sets an alarm: either at a clock time, or a number of minutes from now. |
            | `open_app` | Local | app:text | Opens an installed app by its name. |
            | `dial` | Local | number:text | Opens the dialer with a number typed in. It does not place the call. |
            | `message` | Local | number:text | Opens the messages app with a number typed in. It does not send anything. |
            | `open_url` | Local | url:text | Opens a web address in the browser. |
            | `calendar_event` | Local | title:text, in_minutes:int, duration_minutes:int | Opens a calendar event, prefilled: a title, how long from now it starts, and how long it lasts. |
            | `web_search` | Local | query:text | Hands a query to the browser's own search. |
            | `settings_panel` | Local | panel:enum | Opens an Android settings page: wifi, bluetooth, nfc, airplane, display, sound, battery. |
            | `reply` | LeavesPhone | to:text, text:text | Replies to a message that is waiting as a notification. to: the sender's name or the app, or "that" for the newest message. text: the exact words to send. |
        """.trimIndent()
        assertEquals(expected, Catalogue.markdown(ToolRegistry.builtIn).trimEnd())
    }
}
