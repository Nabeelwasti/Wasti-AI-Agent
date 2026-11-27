package ai.wasti.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusChatHistoryTest {
    @Test
    fun createsStableReadableTitleFromFirstPrompt() {
        val session = NexusChatSession.createOrUpdate(
            existing = null,
            turns = listOf("  Help me plan   a study routine  " to "Start with a weekly outline."),
            remoteTurns = emptyList()
        )
        assertEquals("Help me plan a study routine", session.title)
        assertEquals(1, session.turns.size)
    }

    @Test
    fun preservesExistingTitleWhenAppendingTurns() {
        val original = NexusChatSession(
            id = "session-id",
            title = "My study plan",
            updatedAt = 1L,
            turns = listOf("Plan" to "First answer")
        )
        val updated = NexusChatSession.createOrUpdate(
            existing = original,
            turns = original.turns + ("Next" to "Second answer"),
            remoteTurns = listOf("Next" to "Second answer")
        )
        assertEquals(original.id, updated.id)
        assertEquals("My study plan", updated.title)
        assertEquals(2, updated.turns.size)
        assertEquals(1, updated.remoteTurns.size)
        assertTrue(updated.updatedAt >= original.updatedAt)
    }

    @Test
    fun searchMatchesTitleUserAndAssistantTextCaseInsensitively() {
        val sessions = listOf(
            NexusChatSession("1", "University", 1, listOf("Math assignment" to "Use active recall")),
            NexusChatSession("2", "Work", 2, listOf("Prepare report" to "Production checklist"))
        )
        assertEquals(listOf("1"), NexusChatHistory.search(sessions, "UNIVERSITY").map { it.id })
        assertEquals(listOf("1"), NexusChatHistory.search(sessions, "active recall").map { it.id })
        assertEquals(listOf("2"), NexusChatHistory.search(sessions, "prepare report").map { it.id })
    }

    @Test
    fun exportIncludesBothSidesOfEveryTurn() {
        val session = NexusChatSession(
            "1",
            "Draft",
            1,
            listOf("Write a plan" to "Here is the plan.", "Improve it" to "Add measurable goals.")
        )
        val markdown = NexusChatHistory.exportMarkdown(session)
        assertTrue(markdown.startsWith("# Draft"))
        assertTrue(markdown.contains("## You\n\nWrite a plan"))
        assertTrue(markdown.contains("## WASTI NEXUS\n\nHere is the plan."))
        assertTrue(markdown.contains("Add measurable goals."))
    }
}
