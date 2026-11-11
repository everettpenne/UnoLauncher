package com.jake.duolauncher

import com.jake.duolauncher.search.SmallTalk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmallTalkTest {
    @Test fun aGreetingIsAnsweredWithoutAModel() {
        assertNotNull(SmallTalk.replyTo("Hello, Qwen"))
        assertNotNull(SmallTalk.replyTo("hello"))
        assertNotNull(SmallTalk.replyTo("  HEY   Qwen!  "))
        assertNotNull(SmallTalk.replyTo("thanks"))
        assertNotNull(SmallTalk.replyTo("Thank you, Qwen."))
        assertNotNull(SmallTalk.replyTo("who are you?"))
        assertNotNull(SmallTalk.replyTo("what can you do"))
        assertNotNull(SmallTalk.replyTo("bye"))
    }

    @Test fun theGreetingAndTheAnswerAgreeOnTone() {
        val hello = SmallTalk.replyTo("hello qwen")!!
        assert(hello.contains("Qwen")) { "the reply should say who is talking: $hello" }
        assert(SmallTalk.replyTo("who are you")!!.contains("on this phone")) { "and that it runs on the phone" }
    }

    @Test fun anythingWithMoreToItGoesToTheModel() {
        // A greeting followed by a real question or a request is not small talk: the whole message is matched, never its start.
        assertNull(SmallTalk.replyTo("hello, what's the weather"))
        assertNull(SmallTalk.replyTo("hi, set a timer for 10 minutes"))
        assertNull(SmallTalk.replyTo("thanks, now remind me at 4"))
        assertNull(SmallTalk.replyTo("hello qwen can you explain entropy"))
        assertNull(SmallTalk.replyTo("what are three tips for sleeping better"))
        assertNull(SmallTalk.replyTo(""))
    }

    @Test fun normalisingIgnoresCaseSpacingAndEnds() {
        assertEquals("hello qwen", SmallTalk.normalise("  Hello,   Qwen! "))
        assertEquals("thanks", SmallTalk.normalise("Thanks?"))
        assertEquals("good morning", SmallTalk.normalise("Good    morning."))
    }
}
