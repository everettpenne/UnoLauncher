package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.*
import com.jake.duolauncher.search.agent.tools.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueTest {
    private val tools = ToolRegistry.builtIn

    @Test fun thePromptNamesEveryToolAndSaysHowToReply() {
        val prompt = Catalogue.prompt(tools)
        for (tool in tools) assertTrue("prompt is missing ${tool.name}", prompt.contains(tool.name))
        assertTrue(prompt.contains("\"steps\""))
        assertTrue(prompt.contains("\"clarify\""))
        assertTrue(prompt.contains("\"refuse\""))
        // The tool's own line, so a model can tell what it is for.
        assertTrue(prompt.contains(TimerTool.summary))
    }

    @Test fun theGrammarPinsToolNamesAndParameterNames() {
        val grammar = Catalogue.grammar(tools)
        assertTrue(grammar.startsWith("root ::= plan | clarify | refuse"))
        for (tool in tools) assertTrue("no rule for ${tool.name}", grammar.contains(rule(tool.name)))
        // The tool's own JSON is fixed by the grammar, and its parameter names too (values are validated after).
        assertTrue(grammar.contains("timer-step"))
        assertTrue(grammar.contains("\\\"seconds\\\":"))
        assertTrue(grammar.contains("\\\"label\\\":"))
        // Values are validated afterwards, so the grammar leaves them as digits or text.
        assertTrue(grammar.contains("digits ::="))
        assertTrue(grammar.contains("bool ::="))
        assertTrue(grammar.contains("text ::="))
    }

    @Test fun anEnumsChoicesAreParenthesisedSoTheStepRuleStaysWhole() {
        // A bare alternation (… | "display") makes the step rule accept a step that is only one choice; the model did exactly that.
        val line = Catalogue.grammar(tools).lines().first { it.contains("settings-panel-step ::=") }
        assertTrue("the enum group is not parenthesised: $line", line.contains("( "))
        assertTrue("the step does not close its group: $line", line.trimEnd().endsWith(") \"}}\""))
    }

    @Test fun theHashChangesOnlyWithTheCatalogue() {
        val first = Catalogue.hash(tools)
        assertEquals(16, first.length)
        assertEquals(first, Catalogue.hash(tools))
        assertTrue(first != Catalogue.hash(tools.dropLast(1)))
    }

    @Test fun theMarkdownHasOneRowPerTool() {
        val markdown = Catalogue.markdown(tools)
        val rows = markdown.lines().count { it.startsWith("| `") }
        assertEquals(tools.size, rows)
    }

    @Test fun everyToolDeclaresAnEffectAndHasAUniqueName() {
        assertEquals(tools.size, tools.map { it.name }.toSet().size)
        // Only a reply leaves the phone, so only it asks first; anything else added as LeavesPhone has to be added here on purpose.
        assertEquals(listOf("reply"), tools.filter { it.effect == Effect.LeavesPhone }.map { it.name })
    }

    private fun rule(name: String) = name.replace(Regex("[^a-zA-Z0-9-]"), "-") + "-step"
}
