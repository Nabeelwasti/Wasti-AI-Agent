package ai.wasti.nexus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveModelWorkspaceTest {
    private fun model(
        source: String = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF",
        revision: String = "12145bd1d629190a4d44254073650877954d02c9"
    ) = WorkspaceModel(
        id = "qwen-0.5b",
        family = "Qwen",
        displayName = "WASTI NEXUS · Qwen2.5 0.5B Instruct",
        fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        quantization = "Q4_K_M",
        sizeBytes = 491400032L,
        sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
        status = "ready",
        license = "Apache-2.0",
        source = source,
        sourceRevision = revision,
        minimumAvailableRamGb = 2.5,
        note = "Pinned test model"
    )

    @Test
    fun downloadableModelRequiresPinnedHttpsSourceAndRevision() {
        assertTrue(model().downloadable)
        assertFalse(model(revision = "main").downloadable)
        assertFalse(model(source = "http://huggingface.co/Qwen/model").downloadable)
        assertFalse(model(source = "https://example.com/model").downloadable)
    }

    @Test
    fun downloadableModelRequiresExactSizeAndSha256() {
        assertFalse(model().copy(sizeBytes = null).downloadable)
        assertFalse(model().copy(sizeBytes = 0L).downloadable)
        assertFalse(model().copy(sha256 = null).downloadable)
        assertFalse(model().copy(sha256 = "not-a-checksum").downloadable)
        assertFalse(model().copy(status = "pending_verification").downloadable)
    }
}
