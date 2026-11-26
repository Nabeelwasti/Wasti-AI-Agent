package ai.wasti.nexus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NetworkFallbackPolicyTest {
    @Test
    fun networkFailuresAllowLocalFallback() {
        assertTrue(shouldFallbackToLocal(IOException("No internet connection")))
    }

    @Test
    fun quotaAndTransientServerFailuresAllowLocalFallback() {
        listOf(408, 429, 500, 502, 503, 504).forEach { status ->
            assertTrue("HTTP $status should allow local fallback", shouldFallbackToLocal(
                GatewayHttpException(status, "HTTP $status")
            ))
        }
    }

    @Test
    fun authenticationAndConfigurationErrorsDoNotHideBehindFallback() {
        listOf(400, 401, 403, 404, 410).forEach { status ->
            assertFalse("HTTP $status should remain visible", shouldFallbackToLocal(
                GatewayHttpException(status, "HTTP $status")
            ))
        }
    }

    @Test
    fun missingOnlineCredentialsAllowLocalFallback() {
        assertTrue(shouldFallbackToLocal(IllegalArgumentException("API key must be at least 32 characters.")))
        assertTrue(shouldFallbackToLocal(IllegalArgumentException("Gateway URL must use HTTPS.")))
    }

    @Test
    fun unrelatedValidationErrorsDoNotTriggerFallback() {
        assertFalse(shouldFallbackToLocal(IllegalArgumentException("Unsupported model format.")))
    }
}
