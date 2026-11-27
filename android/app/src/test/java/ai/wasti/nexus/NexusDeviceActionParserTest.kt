package ai.wasti.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusDeviceActionParserTest {
    @Test fun parsesNarrowSafeActions() {
        assertEquals(NexusDeviceAction.ScrollForward, NexusDeviceActionParser.parse("scroll down")?.action)
        assertEquals(NexusDeviceAction.ScrollBackward, NexusDeviceActionParser.parse("scroll up")?.action)
        assertEquals(NexusDeviceAction.Back, NexusDeviceActionParser.parse("go back")?.action)
        assertEquals(NexusDeviceAction.Home, NexusDeviceActionParser.parse("go home")?.action)
        assertEquals(NexusDeviceAction.Notifications, NexusDeviceActionParser.parse("open notifications")?.action)
        assertEquals(NexusDeviceAction.ReadScreen, NexusDeviceActionParser.parse("read current screen")?.action)
    }

    @Test fun parsesExactLabelClicksAndRejectsHighImpactLabels() {
        val draft = NexusDeviceActionParser.parse("tap the Settings button")
        assertTrue(draft?.action is NexusDeviceAction.ClickLabel)
        assertEquals("Settings", (draft?.action as NexusDeviceAction.ClickLabel).label)
        assertNull(NexusDeviceActionParser.parse("click Delete"))
        assertNull(NexusDeviceActionParser.parse("tap Send"))
        assertNull(NexusDeviceActionParser.parse("click Allow"))
        assertNull(NexusDeviceActionParser.parse("click Transfer money"))
    }

    @Test fun ordinaryConversationIsNotTreatedAsDeviceCommand() {
        assertNull(NexusDeviceActionParser.parse("How do I scroll down in a PDF?"))
        assertNull(NexusDeviceActionParser.parse("Please explain Android notifications"))
        assertNull(NexusDeviceActionParser.parse("tap"))
    }
}
