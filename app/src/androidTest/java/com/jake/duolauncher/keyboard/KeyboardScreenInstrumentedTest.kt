package com.jake.duolauncher.keyboard

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The keyboard's screen composed on its own (no input method service), driven through its recorded actions. */
@RunWith(AndroidJUnit4::class)
class KeyboardScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private class Recorder : KeyboardActions {
        val log = mutableListOf<String>()
        override fun type(text: String) { log += "type:$text" }
        override fun replaceLast(text: String) { log += "replace:$text" }
        override fun backspace(held: Int) { log += "backspace:$held" }
        override fun space() { log += "space" }
        override fun enter() { log += "enter" }
        override fun globe() { log += "globe" }
        override fun shift() { log += "shift" }
        override fun page(target: KeyPage) { log += "page:$target" }
        override fun haptic(kind: HapticKind) = Unit
        override fun pick(suggestion: Suggestion) { log += "pick" }
        override fun moveCursor(delta: Int) { log += "cursor:$delta" }
        override fun emoji(text: String) { log += "emoji:$text" }
        override fun paste() { log += "paste" }
        override fun setOneHanded(mode: Int) { log += "oneHanded:$mode" }
    }

    private fun show(ui: KeyboardUiState = KeyboardUiState(), actions: KeyboardActions = Recorder()) {
        compose.setContent { KeyboardScreen(ui, actions) }
    }

    private fun keyNodes() = compose.onAllNodes(SemanticsMatcher("key") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("key-") == true &&
            node.config.getOrNull(SemanticsProperties.TestTag) != "key-preview" })

    @Test fun tappingAKeyTypesItsLetter() {
        val actions = Recorder()
        show(actions = actions)
        compose.onNodeWithTag("key-q").performClick()
        compose.waitForIdle()
        assertTrue("typed: ${actions.log}", actions.log.any { it.startsWith("type:") })
    }

    @Test fun specialKeysCallTheirActions() {
        val actions = Recorder()
        show(actions = actions)
        compose.onNodeWithTag("key-shift").performClick()
        compose.onNodeWithTag("key-enter").performClick()
        compose.onNodeWithTag("key-globe").performClick()
        compose.waitForIdle()
        assertTrue(actions.log.containsAll(listOf("shift", "enter", "globe")))
    }

    @Test fun emojiKeyAsksForTheEmojiPage() {
        val actions = Recorder()
        show(actions = actions)
        compose.onNodeWithTag("key-emoji").performClick()
        compose.waitForIdle()
        assertTrue(actions.log.contains("page:${KeyPage.EMOJI}"))
    }

    @Test fun emojiPageReplacesTheLetterKeys() {
        val ui = KeyboardUiState().apply { page = KeyPage.EMOJI }
        show(ui)
        compose.onNodeWithTag("emoji-panel").assertIsDisplayed()
    }

    @Test fun passwordFieldsHaveNoSuggestionLane() {
        val ui = KeyboardUiState().apply { password = true }
        show(ui)
        compose.onAllNodes(SemanticsMatcher("lane") { it.config.getOrNull(SemanticsProperties.TestTag) == "suggestion-strip" })
            .assertCountEquals0()
    }

    @Test fun oneHandedModeOffersToExpandAndCallsBack() {
        val actions = Recorder()
        val ui = KeyboardUiState().apply { oneHanded = 1 }
        show(ui, actions)
        compose.onNodeWithTag("one-handed-expand").performClick()
        compose.waitForIdle()
        assertTrue(actions.log.contains("oneHanded:0"))
    }

    @Test fun noTwoKeysOverlap() {
        show()
        compose.waitForIdle()
        val boxes = mutableListOf<Pair<String, DpRect>>()
        val nodes = keyNodes().fetchSemanticsNodes()
        assertTrue("keys were composed", nodes.size > 25)
        val density = compose.density
        nodes.forEach { node ->
            val tag = node.config[SemanticsProperties.TestTag]
            val r = node.boundsInRoot
            boxes += tag to DpRect(with(density) { r.left.toDp() }, with(density) { r.top.toDp() }, with(density) { r.right.toDp() }, with(density) { r.bottom.toDp() })
        }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val a = boxes[i].second; val b = boxes[j].second
            val overlapX = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val overlapY = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            assertTrue("${boxes[i].first} overlaps ${boxes[j].first}", !(overlapX > 0.5.dp && overlapY > 0.5.dp))
        }
    }

    @Test fun everyKeyIsAtLeastATouchTargetTall() {
        show()
        compose.waitForIdle()
        keyNodes().fetchSemanticsNodes().forEach { node ->
            val height = with(compose.density) { (node.boundsInRoot.bottom - node.boundsInRoot.top).toDp() }
            assertTrue("${node.config[SemanticsProperties.TestTag]} is only $height tall", height >= 30.dp)
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals0() {
        assertEquals(0, fetchSemanticsNodes().size)
    }
}
