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

    @Test fun acceptsValidFutureReminder() {
        assertNull(NexusReminderPolicy.validate("Submit assignment", 60_000L, 1_000L))
    }
}
