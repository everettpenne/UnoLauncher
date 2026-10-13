package com.jake.duolauncher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Words the person reads live in `res/values/strings.xml`, not in code, so wording can be fixed in one place and the app can be
 * translated. This guards the rule: a `Text("plain words")` in the sources fails the build. Text built from values
 * (`Text("${count} apps")`) is not yet covered; use a format string in strings.xml for new ones.
 */
class UiStringsTest {
    private val plainText = Regex("""\bText\(\s*"((?:[^"\\$]|\\.)*)"""")
    // Text that is a symbol or glyph, not words, may stay in code.
    private val allowed = setOf("«", "‹", "›", "»", "✓", "•")

    private fun sources(): List<File> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test fun noPlainWordsAreWrittenInCode() {
        val found = sources().flatMap { file ->
            plainText.findAll(file.readText()).map { it.groupValues[1] }.filter { it !in allowed }.map { "${file.name}: \"$it\"" }.toList()
        }
        assertTrue("Move these into res/values/strings.xml and use stringResource():\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test fun everyStringNameIsUniqueAndValid() {
        val xml = File(listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml")).first { it.exists() }.path).readText()
        val names = Regex("""<string name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
        assertTrue("duplicate string names", names.size == names.toSet().size)
        assertTrue(names.all { Regex("[a-z][a-z0-9_]*").matches(it) })
    }
}
