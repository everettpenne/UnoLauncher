package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.Catalogue
import com.jake.duolauncher.search.agent.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The generated docs must match the registry: adding a tool without regenerating `docs/agent/tools.md` fails here. */
class CatalogueDocsTest {
    private val docs = File("../docs/agent/tools.md")

    @Test fun theToolsDocMatchesTheRegistry() {
        assertTrue("docs/agent/tools.md is missing", docs.exists())
        assertEquals(Catalogue.markdown(ToolRegistry.builtIn).trimEnd(), docs.readText().trimEnd())
    }
}
