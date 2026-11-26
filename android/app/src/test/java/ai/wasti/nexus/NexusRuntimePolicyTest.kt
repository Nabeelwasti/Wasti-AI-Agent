package ai.wasti.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NexusRuntimePolicyTest {
    private fun snapshot(
        path: String = "",
        loaded: Boolean = false,
        ram: Long = 8L * 1024 * 1024 * 1024,
        thermal: Int = 0,
        battery: Int? = 80,
        charging: Boolean = false,
        internet: Boolean = false,
        wifi: Boolean = false,
        cellular: Boolean = false,
        metered: Boolean = false
    ) = NexusRuntimeSnapshot(
        internetValidated = internet, wifiConnected = wifi, cellularConnected = cellular,
        metered = metered, availableRamBytes = ram, totalRamBytes = 12L * 1024 * 1024 * 1024,
        thermalStatus = thermal, batteryPercent = battery, charging = charging,
        localModelPath = path, localModelLoaded = loaded
    )

    @Test fun offlineNeverRoutesToInternet() {
        val decision = NexusRuntimePolicy.decide(NexusMode.OFFLINE, snapshot(internet = true), true, prompt = "Hello")
        assertEquals(NexusRoute.BLOCKED, decision.route)
    }

    @Test fun autoUsesLocalWhenModelAndHeadroomAreAvailable() {
        val model = File.createTempFile("nexus-policy", ".gguf")
        try {
            model.writeBytes(ByteArray(1024 * 1024))
            val decision = NexusRuntimePolicy.decide(NexusMode.AUTO, snapshot(path = model.absolutePath), false, prompt = "Hello")
            assertEquals(NexusRoute.LOCAL, decision.route)
        } finally { model.delete() }
    }

    @Test fun autoChoosesRemoteWhenLocalMemoryIsInsufficientAndInternetIsValidated() {
        val model = File.createTempFile("nexus-policy", ".gguf")
        try {
            model.writeBytes(ByteArray(1024 * 1024))
            val decision = NexusRuntimePolicy.decide(
                NexusMode.AUTO, snapshot(path = model.absolutePath, ram = 512L * 1024 * 1024, internet = true, cellular = true, metered = true),
                remoteConfigured = true, prompt = "Explain this"
            )
            assertEquals(NexusRoute.REMOTE, decision.route)
            assertTrue(decision.reason.contains("mobile data"))
            assertTrue(decision.reason.contains("Metered"))
        } finally { model.delete() }
    }

    @Test fun autoBlocksWhenOfflineAndNoLocalModelExists() {
        val decision = NexusRuntimePolicy.decide(NexusMode.AUTO, snapshot(), true, prompt = "Hello")
        assertEquals(NexusRoute.BLOCKED, decision.route)
    }

    @Test fun offlineCanUseLoopbackTermuxServerWithoutInternet() {
        val decision = NexusRuntimePolicy.decide(NexusMode.OFFLINE, snapshot(), false, localServerAvailable = true, prompt = "Hello")
        assertEquals(NexusRoute.TERMUX, decision.route)
    }

    @Test fun modalityTasksAreNotPretendedToBeSupportedByTextOnlyLocalModel() {
        val decision = NexusRuntimePolicy.decide(NexusMode.AUTO, snapshot(internet = false), false, prompt = "Generate image of a red car")
        assertEquals(NexusTask.IMAGE_GENERATION, decision.task)
        assertEquals(NexusRoute.BLOCKED, decision.route)
    }

    @Test fun thermalAndBatteryGuardsApplyEvenWhenModelIsLoaded() {
        val model = File.createTempFile("nexus-policy", ".gguf")
        try {
            model.writeBytes(ByteArray(1024))
            val hot = NexusRuntimePolicy.decide(NexusMode.AUTO, snapshot(path = model.absolutePath, loaded = true, thermal = 3), false, prompt = "Hello")
            val lowBattery = NexusRuntimePolicy.decide(NexusMode.AUTO, snapshot(path = model.absolutePath, loaded = true, battery = 5), false, prompt = "Hello")
            assertEquals(NexusRoute.BLOCKED, hot.route)
            assertEquals(NexusRoute.BLOCKED, lowBattery.route)
        } finally { model.delete() }
    }

    @Test fun onlyTextTasksAreCurrentlyEligibleForLocalChatRuntime() {
        assertTrue(NexusRuntimePolicy.localCanHandle(NexusTask.GENERAL, "qwen3-1.7b.gguf"))
        assertTrue(NexusRuntimePolicy.localCanHandle(NexusTask.CODING, "qwen2.5-coder-1.5b.gguf"))
        assertFalse(NexusRuntimePolicy.localCanHandle(NexusTask.VISION, "qwen3-1.7b.gguf"))
        assertFalse(NexusRuntimePolicy.localCanHandle(NexusTask.TRAINING, "qwen3-1.7b.gguf"))
    }

    @Test fun autoSelectsTaskSpecialistFromInstalledModelsWithinRamBudget() {
        val coder = File.createTempFile("qwen-coder", ".gguf")
        val general = File.createTempFile("qwen3-1.7b", ".gguf")
        try {
            coder.writeBytes(ByteArray(20 * 1024 * 1024))
            general.writeBytes(ByteArray(20 * 1024 * 1024))
            val paths = listOf(general.absolutePath, coder.absolutePath)
            val coding = NexusRuntimePolicy.chooseLocalModelPath(NexusTask.CODING, paths, 4L * 1024 * 1024 * 1024, general.absolutePath)
            val chat = NexusRuntimePolicy.chooseLocalModelPath(NexusTask.GENERAL, paths, 4L * 1024 * 1024 * 1024, general.absolutePath)
            assertEquals(coder.absolutePath, coding)
            assertEquals(general.absolutePath, chat)
        } finally {
            coder.delete()
            general.delete()
        }
    }

    @Test fun autoDoesNotChooseLocalModelWhenNoCandidateFitsAvailableRam() {
        val model = File.createTempFile("qwen3", ".gguf")
        try {
            model.writeBytes(ByteArray(1024 * 1024))
            val chosen = NexusRuntimePolicy.chooseLocalModelPath(
                NexusTask.GENERAL, listOf(model.absolutePath), 128L * 1024 * 1024, model.absolutePath
            )
            assertEquals("", chosen)
        } finally {
            model.delete()
        }
    }

    @Test fun termuxFallbackIsBlockedWhenDeviceIsHotOrCriticallyLowOnBattery() {
        val hot = NexusRuntimePolicy.decide(
            NexusMode.OFFLINE, snapshot(thermal = 3), false, localServerAvailable = true, prompt = "Hello"
        )
        val lowBattery = NexusRuntimePolicy.decide(
            NexusMode.OFFLINE, snapshot(battery = 5), false, localServerAvailable = true, prompt = "Hello"
        )
        assertEquals(NexusRoute.BLOCKED, hot.route)
        assertEquals(NexusRoute.BLOCKED, lowBattery.route)
    }
