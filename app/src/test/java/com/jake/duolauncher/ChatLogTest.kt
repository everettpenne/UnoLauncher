package com.jake.duolauncher

import com.jake.duolauncher.search.Chat
import com.jake.duolauncher.search.ChatLayout
import com.jake.duolauncher.search.ChatLog
import com.jake.duolauncher.search.ChatPull
import com.jake.duolauncher.search.ChatTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLogTest {
    private fun turn(at: Long, said: String = "set a timer", reply: String = "Timer set", kind: ChatTurn.Kind = ChatTurn.Kind.DONE) = ChatTurn(at, said, reply, kind)
    private val minute = 60_000L

    @Test fun turnsCloseTogetherAreOneConversationAndALongGapStartsAnother() {
        val (one, id) = ChatLog.add(emptyList(), null, turn(1_000))
        val (two, same) = ChatLog.add(one, id, turn(1_000 + 5 * minute, "make it 15", "Timer set for 15 minutes"))
        assertEquals(id, same); assertEquals(1, two.size); assertEquals(2, two.single().turns.size)
        val (three, next) = ChatLog.add(two, same, turn(1_000 + 5 * minute + ChatLog.NEW_AFTER_MS, "why is the sky blue", "Scattering.", ChatTurn.Kind.ANSWER))
        assertTrue(next != id); assertEquals(2, three.size)
        assertEquals("set a timer", three.first { it.id == id }.title)
        // With no conversation chosen (a new chat), the turn starts one.
        assertEquals(3, ChatLog.add(three, null, turn(9_000_000_000)).first.size)
    }

    @Test fun onlySoMuchIsKept() {
        var chats = emptyList<Chat>(); var id: Long? = null
        repeat(ChatLog.MAX_TURNS + 20) { i -> ChatLog.add(chats, id, turn(1_000L + i)).let { chats = it.first; id = it.second } }
        assertEquals(ChatLog.MAX_TURNS, chats.single().turns.size)
        assertEquals(1_000L + ChatLog.MAX_TURNS + 19, chats.single().turns.last().atMs)
        var many = emptyList<Chat>()
        repeat(ChatLog.MAX_CHATS + 5) { i -> many = ChatLog.add(many, null, turn(i * 10L * ChatLog.NEW_AFTER_MS)).first }
        assertEquals(ChatLog.MAX_CHATS, many.size)
        // The oldest went.
        assertTrue(many.none { it.lastMs == 0L })
        assertEquals(ChatLog.MAX_TEXT, ChatLog.add(emptyList(), null, turn(1, said = "x".repeat(9000))).first.single().turns.single().said.length)
    }

    @Test fun aConversationIsDeletedAndTheRestStay() {
        val (a, first) = ChatLog.add(emptyList(), null, turn(1_000))
        val (b, second) = ChatLog.add(a, null, turn(1_000 + 2 * ChatLog.NEW_AFTER_MS, "open maps", "Maps open"))
        val left = ChatLog.delete(b, first)
        assertEquals(listOf(second), left.map { it.id })
        assertTrue(ChatLog.delete(left, second).isEmpty())
        assertEquals(listOf(second, first), ChatLog.listed(b).map { it.id })
    }

    @Test fun theHistorySurvivesBeingWrittenAndReadAndADamagedFileIsEmpty() {
        val (chats, _) = ChatLog.add(emptyList(), null, turn(5, "note \"milk\"\nAnswer: yes", "Saved: “milk”"))
        assertEquals(chats, ChatLog.fromJson(ChatLog.toJson(chats)))
        assertTrue(ChatLog.fromJson("not json").isEmpty())
        assertTrue(ChatLog.fromJson(null).isEmpty())
        assertTrue(ChatLog.fromJson("""[{"id":1,"turns":[{"at":1,"said":"x","reply":"y","kind":"NOPE"}]}]""").isEmpty())
        assertEquals("[]", ChatLog.toJson(emptyList()))
    }

    @Test fun theNextQuestionGoesWithTheLastFewExchanges() {
        val chat = Chat(1, listOf(
            turn(0, "what is a fjord", "A long, narrow sea inlet between steep cliffs, carved by a glacier.", ChatTurn.Kind.ANSWER),
            turn(minute, "set a timer for 10 minutes", "Timer set for 10 minutes"),
            turn(2 * minute, "set a timer\nYou asked: How long?\nAnswer: 5 minutes", "Timer set for 5 minutes"),
            turn(3 * minute, "order a pizza", "I cannot place orders from here.", ChatTurn.Kind.REFUSED)))
        val block = ChatLog.earlier(chat, 4 * minute)
        assertTrue(block.startsWith("Earlier in this conversation (oldest first):\n"))
        // The last three, oldest first; an answered question shows the answer that was given, not the scaffolding round it.
        assertFalse(block.contains("fjord"))
        assertTrue(block.contains("- They said: set a timer for 10 minutes\n  You did: Timer set for 10 minutes"))
        assertTrue(block.contains("- They said: 5 minutes\n  You did: Timer set for 5 minutes"))
        assertTrue(block.endsWith("- They said: order a pizza\n  You said: I cannot place orders from here."))
        // A conversation that has gone quiet is not sent, and nor is nothing.
        assertEquals("", ChatLog.earlier(chat, 4 * minute + ChatLog.NEW_AFTER_MS))
        assertEquals("", ChatLog.earlier(null, 0))
        // Long turns are cut, and the whole stays inside its budget by dropping the oldest exchange.
        val long = Chat(2, List(3) { i -> turn(i * minute, "q$i " + "x".repeat(400), "a$i " + "y".repeat(900), ChatTurn.Kind.ANSWER) })
        val cut = ChatLog.earlier(long, 3 * minute)
        assertTrue(cut.length <= ChatLog.EARLIER_BUDGET + 60)
        assertFalse(cut.contains("q0 ")); assertTrue(cut.contains("q2 "))
    }

    @Test fun aPullOnTheHandleOpensAndShutsTheConversation() {
        assertFalse(ChatPull.settles(open = false, dragDp = 20f, commitDp = 48f))
        assertTrue(ChatPull.settles(open = false, dragDp = 48f, commitDp = 48f))
        assertTrue(ChatPull.settles(open = true, dragDp = -20f, commitDp = 48f))
        assertFalse(ChatPull.settles(open = true, dragDp = -48f, commitDp = 48f))
        // The card grows under a pull down while shut, and not for a pull up or while open.
        assertEquals(40f, ChatPull.grownDp(false, 40f)); assertEquals(0f, ChatPull.grownDp(false, -40f)); assertEquals(0f, ChatPull.grownDp(true, 40f))
        assertEquals("now", ChatPull.ago(1_000, 1_500)); assertEquals("5 min ago", ChatPull.ago(0, 5 * minute)); assertEquals("3 h ago", ChatPull.ago(0, 200 * minute))
        assertEquals("yesterday", ChatPull.ago(0, 30 * 60 * minute)); assertEquals("12 days ago", ChatPull.ago(0, 12 * 24 * 60 * minute))
    }

    // ---------------------------------------------------------------- the layout, from what the phone reports

    private val phone = ChatLayout.metrics(widthDp = 380f, lineDp = 22f, cornerDp = 40f, touchDp = 48f)
    private val small = ChatLayout.metrics(widthDp = 300f, lineDp = 22f, cornerDp = 24f, touchDp = 48f)
    private val tablet = ChatLayout.metrics(widthDp = 560f, lineDp = 22f, cornerDp = 32f, touchDp = 48f)
    private val largeText = ChatLayout.metrics(widthDp = 380f, lineDp = 22f * 1.6f, cornerDp = 40f, touchDp = 48f)
    private val square = ChatLayout.metrics(widthDp = 380f, lineDp = 22f, cornerDp = 0f, touchDp = 48f)
    private val all = listOf(phone, small, tablet, largeText, square)

    @Test fun everythingAFingerHitsIsAtLeastTheSystemsTouchSize() {
        for (m in all) {
            assertTrue(m.fieldHeight >= m.touch)
            assertTrue(m.commitPull >= m.touch)
            assertTrue("the send button is inside the field", m.sendSize < m.fieldHeight && m.sendSize > m.fieldHeight / 2f)
            assertTrue("a swipe to close starts anywhere on the field's row", m.closeZone >= m.fieldHeight + m.inset)
        }
    }

    @Test fun textStartsOnOneLineAndClearsTheBodysCorner() {
        for (m in all) {
            // Text is never nearer the side than the shapes are, plus the padding inside one: the reply's words, the field's and a card's line up.
            assertTrue(m.edge >= m.inset + m.bubblePadH - .01f)
            assertTrue(m.inset > 0f && m.edge < 380f / 4f)
        }
        // A rounder body pushes text further in; a square one still leaves half a line.
        assertTrue(phone.edge > square.edge)
        assertEquals(11f, square.inset, .01f)
        // The field's round end is concentric with the body's corner: its radius and its inset add up to the corner.
        assertEquals(40f, phone.inset + phone.fieldHeight / 2f, .01f)
    }

    @Test fun aBubbleIsRecognisablyTheirsAndALineIsNeverTooLongToRead() {
        for (m in all) {
            assertTrue("a bubble leaves the far side clear", m.bubbleMax <= m.replyMax)
            assertTrue(m.bubbleMax >= m.touch)
            // One line of text in a bubble is a pill.
            assertEquals(m.bubbleCorner * 2f, m.bubblePadV * 2f + (m.icon), .01f)
        }
        // On a phone the width decides; on a tablet the reading measure does, so lines do not run the width of the screen.
        assertTrue(phone.bubbleMax < 20f * 22f)
        assertEquals(20f * 22f, tablet.bubbleMax, .01f)
        assertTrue(tablet.replyMax <= 26f * 22f)
        assertTrue(small.bubbleMax < phone.bubbleMax)
    }

    @Test fun theLayoutFollowsThePersonsTextSize() {
        assertTrue(largeText.fieldHeight > phone.fieldHeight)
        assertTrue(largeText.gapBetween > phone.gapBetween && largeText.bubblePadH > phone.bubblePadH && largeText.icon > phone.icon)
        // An exchange reads as a pair: the gap inside one is well under the gap between two.
        for (m in all) assertTrue(m.gapWithin * 2f < m.gapBetween)
    }

    @Test fun theTimeIsSaidAfterAPauseAndOnTheLastTurn() {
        val turns = listOf(turn(0), turn(minute), turn(2 * minute + ChatLayout.STAMP_GAP_MS), turn(3 * minute + ChatLayout.STAMP_GAP_MS))
        assertEquals(listOf(false, true, false, true), turns.indices.map { ChatLayout.stamped(turns, it) })
    }
}
