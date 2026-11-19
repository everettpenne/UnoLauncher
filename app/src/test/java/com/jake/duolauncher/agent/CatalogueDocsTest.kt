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
            | `timer` | Local | length:text, label:text | Starts a countdown timer that the island shows. length: how long, in the person's words. |
            | `stopwatch` | Local | action:enum | Starts, stops or resets the island's stopwatch. |
            | `torch` | Local | on:bool | Turns the flashlight on or off. |
            | `focus` | Local | on:bool | Turns Focus (Do Not Disturb) on or off; it hides the apps the person picked. |
            | `note` | Local | text:text | Saves a note to the Note widget on Home. |
            | `alarm` | Local | when:text | Sets an alarm. when: a clock time or how long from now, in the person's words. |
            | `open_app` | Local | app:text | Opens an installed app by its name. |
            | `dial` | Local | number:text | Opens the dialer with a number typed in. It does not place the call. |
            | `message` | Local | number:text, text:text | Opens the messages app at a phone number, with the words typed in if any were given. It does not send anything. |
            | `open_url` | Local | url:text | Opens a web address in the browser. |
            | `calendar_event` | Local | title:text, start:text, length:text | Opens a calendar event, prefilled. start: when it begins, in the person's words. length: how long it lasts, if they said. |
            | `web_search` | Local | query:text | Hands a query to the browser's own search. |
            | `settings_panel` | Local | panel:enum | Opens an Android settings page: wifi, bluetooth, nfc, airplane, display, sound, battery. |
            | `reply` | LeavesPhone | to:text, text:text | Replies to a message that is waiting as a notification. to: the sender's name or the app, or "that" for the newest message. text: the exact words to send. |
            | `timer_cancel` | Local | — | Cancels the running countdown timer. |
            | `media` | Local | action:enum | Controls what is playing: play, pause, next track, previous track. |
            | `volume` | Local | level:text | Changes the media volume. level: up, down, mute, max, or a percentage the person said. |
            | `checklist_add` | Local | item:text | Adds one item to the Checklist widget on Home (a shopping or to-do list). |
            | `directions` | Local | place:text | Opens the maps app at a place the person named. |
        """.trimIndent()
        assertEquals(expected, Catalogue.markdown(ToolRegistry.builtIn).trimEnd())
    }
}
