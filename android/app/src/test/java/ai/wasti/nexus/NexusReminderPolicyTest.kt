package ai.wasti.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NexusReminderPolicyTest {
    @Test fun rejectsBlankTitle() {
        assertEquals("Enter a reminder title.", NexusReminderPolicy.validate("  ", 2_000L, 1_000L))
    }

    @Test fun rejectsPastAndCurrentTimes() {
        assertEquals("Choose a time in the future.", NexusReminderPolicy.validate("Call home", 1_000L, 1_000L))
        assertEquals("Choose a time in the future.", NexusReminderPolicy.validate("Call home", 999L, 1_000L))
    }

    @Test fun rejectsLongTitlesAndDatesBeyondOneYear() {
        assertEquals("Reminder titles must be 120 characters or fewer.", NexusReminderPolicy.validate("x".repeat(121), 2_000L, 1_000L))
        assertEquals("Choose a date within the next year.", NexusReminderPolicy.validate("Review", 1_000L + 366L * 24 * 60 * 60 * 1000, 1_000L))
    }

    @Test fun parsesRelativeReminderWithExplicitAction() {
        val now = 1_700_000_000_000L
        val draft = NexusReminderParser.parse("Remind me to call home in 15 minutes", now)
        assertEquals("call home", draft?.title)
        assertEquals(now + 15 * 60 * 1000L, draft?.triggerAtMillis)
    }

    @Test fun parsesTomorrowReminderAndRejectsAmbiguousCommands() {
        val now = java.time.LocalDateTime.of(2026, 11, 27, 10, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val draft = NexusReminderParser.parse("remind me to submit assignment tomorrow at 5:30 pm", now)
        assertEquals("submit assignment", draft?.title)
        assertEquals(null, NexusReminderParser.parse("remind me about this later", now))
        assertEquals(null, NexusReminderParser.parse("remind me to call home at 5 pm tomorrow", now))
    }

    @Test fun nextReminderIdAvoidsExistingIds() {
        val existing = listOf(NexusReminder(7, "existing", 99_999L))
        assertEquals(8, NexusReminderParser.nextId(existing, 7L))
    }

    @Test fun acceptsValidFutureReminder() {
        assertNull(NexusReminderPolicy.validate("Submit assignment", 60_000L, 1_000L))
    }
}
