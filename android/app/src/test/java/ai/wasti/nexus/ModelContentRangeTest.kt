package ai.wasti.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelContentRangeTest {
    @Test
    fun parsesValidPartialContentRange() {
        val range = parseModelContentRange("bytes 1048576-2097151/491000000")
        requireNotNull(range)
        assertEquals(1_048_576L, range.first)
        assertEquals(2_097_151L, range.last)
        assertEquals(491_000_000L, range.total)
    }

    @Test
    fun rejectsMissingMalformedAndImpossibleRanges() {
        assertNull(parseModelContentRange(null))
        assertNull(parseModelContentRange("bytes */491000000"))
        assertNull(parseModelContentRange("bytes 10-9/100"))
        assertNull(parseModelContentRange("bytes 1-2/*"))
        assertNull(parseModelContentRange("items 1-2/100"))
    }

    @Test
    fun acceptsOnlyNumericContentRangeFields() {
        assertNull(parseModelContentRange("bytes start-end/total"))
        assertNull(parseModelContentRange("bytes 1-2/999999999999999999999999"))
    }
}
