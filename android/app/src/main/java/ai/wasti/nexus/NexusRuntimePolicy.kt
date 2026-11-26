package ai.wasti.nexus

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.app.ActivityManager
import java.io.File

internal enum class NexusTask { GENERAL, REASONING, CODING, VISION, IMAGE_GENERATION, SPEECH_TO_TEXT, TEXT_TO_SPEECH, TRAINING }
internal enum class NexusRoute { LOCAL, TERMUX, REMOTE, BLOCKED }
internal enum class NexusMode { AUTO, OFFLINE, ONLINE }

internal data class NexusRuntimeSnapshot(
    val internetValidated: Boolean,
    val wifiConnected: Boolean,
    val cellularConnected: Boolean,
    val metered: Boolean,
    val availableRamBytes: Long,
    val totalRamBytes: Long,
    val thermalStatus: Int,
    val batteryPercent: Int?,
    val charging: Boolean,
    val localModelPath: String,
    val localModelLoaded: Boolean
)

internal data class NexusRouteDecision(val route: NexusRoute, val task: NexusTask, val reason: String)

internal object NexusRuntimePolicy {
    fun isLoopbackHttp(root: String): Boolean = runCatching {
        val uri = java.net.URI(root)
        uri.scheme == "http" && uri.host?.lowercase() == "localhost"
    }.getOrDefault(false)

    private const val GIB = 1024L * 1024L * 1024L
    private const val LOCAL_RESERVE_BYTES = 1_229L * 1024L * 1024L
    private const val MIN_TERMUX_HEADROOM_BYTES = 768L * 1024L * 1024L
    private const val SEVERE_THERMAL_STATUS = 3
    private const val LOW_BATTERY_PERCENT = 12

    fun classify(prompt: String): NexusTask {
        val value = prompt.lowercase()
        return when {
            listOf("generate image", "create an image", "draw me", "image generation", "flux").any(value::contains) -> NexusTask.IMAGE_GENERATION
            listOf("transcribe", "speech to text", "voice note", "audio transcription").any(value::contains) -> NexusTask.SPEECH_TO_TEXT
            listOf("read aloud", "text to speech", "speak this", "voice output").any(value::contains) -> NexusTask.TEXT_TO_SPEECH
            listOf("analyze this image", "what is in this picture", "describe this photo", "visual inspection").any(value::contains) -> NexusTask.VISION
            listOf("fine tune", "fine-tune", "lora", "qlora", "train an adapter").any(value::contains) -> NexusTask.TRAINING
            listOf("debug", "write code", "program", "python", "kotlin", "javascript", "stack trace", "compile").any(value::contains) -> NexusTask.CODING
            listOf("compare", "evaluate", "strategy", "analyze", "reason", "plan", "math proof").any(value::contains) -> NexusTask.REASONING
            else -> NexusTask.GENERAL
        }
    }

    fun estimatedLocalWorkingSet(modelPath: String): Long {
        val modelBytes = runCatching { File(modelPath).takeIf { it.isFile }?.length() ?: 0L }.getOrDefault(0L)
        if (modelBytes <= 0L) return Long.MAX_VALUE
        return (modelBytes.toDouble() * 2.5).toLong() + LOCAL_RESERVE_BYTES
    }

    fun localCanHandle(task: NexusTask, modelPath: String): Boolean {
        val name = modelPath.substringAfterLast('/').lowercase()
        return when (task) {
            NexusTask.GENERAL -> true
            NexusTask.REASONING -> true
            NexusTask.CODING -> name.contains("coder") || name.contains("qwen3") || name.contains("deepseek-r1")
            else -> false
        }
    }

    fun localServerCanHandle(task: NexusTask, modelId: String): Boolean {
        val name = modelId.lowercase()
        return when (task) {
            NexusTask.GENERAL -> true
            NexusTask.REASONING -> name.contains("deepseek-r1") || name.contains("qwen3") || name.contains("qwen")
            NexusTask.CODING -> name.contains("coder") || name.contains("qwen3") || name.contains("deepseek-r1")
            else -> false
        }
    }

    fun chooseLocalModelPath(task: NexusTask, installedPaths: List<String>, availableRamBytes: Long, selectedPath: String): String {
        if (task !in setOf(NexusTask.GENERAL, NexusTask.REASONING, NexusTask.CODING)) return ""
        val existing = installedPaths.filter { path ->
            runCatching { File(path).isFile && File(path).extension.equals("gguf", ignoreCase = true) }.getOrDefault(false)
        }
        val ranked = when (task) {
            NexusTask.CODING -> listOf("coder", "qwen3", "deepseek-r1", "qwen2.5-1.5b", "granite")
            NexusTask.REASONING -> listOf("deepseek-r1", "qwen3-1.7b", "qwen2.5-1.5b", "granite", "qwen")
            else -> listOf("qwen3-1.7b", "qwen2.5-1.5b", "qwen3-0.6b", "qwen2.5-0.5b", "smollm2-1.7b", "granite", "tinyllama")
        }
        val fitting = existing.filter { estimatedLocalWorkingSet(it) <= availableRamBytes }
        val chosen = fitting.minByOrNull { path ->
            val name = path.substringAfterLast('/').lowercase()
            val index = ranked.indexOfFirst { name.contains(it) }
            if (index < 0) ranked.size else index
        }
        if (chosen != null) return chosen
        return selectedPath.takeIf { it in existing && estimatedLocalWorkingSet(it) <= availableRamBytes }.orEmpty()
    }

    fun decide(mode: NexusMode, snapshot: NexusRuntimeSnapshot, remoteConfigured: Boolean, localServerAvailable: Boolean = false, prompt: String): NexusRouteDecision {
        val task = classify(prompt)
        val modelExists = snapshot.localModelPath.isNotBlank() && File(snapshot.localModelPath).isFile
        val memoryEnough = modelExists && snapshot.availableRamBytes >= estimatedLocalWorkingSet(snapshot.localModelPath)
        val thermalSafe = snapshot.thermalStatus < SEVERE_THERMAL_STATUS
        val batterySafe = snapshot.batteryPercent == null || snapshot.batteryPercent >= LOW_BATTERY_PERCENT || snapshot.charging
        val localSafe = localCanHandle(task, snapshot.localModelPath) && modelExists && (snapshot.localModelLoaded || memoryEnough) && thermalSafe && batterySafe
        val termuxSafe = localServerAvailable && snapshot.availableRamBytes >= MIN_TERMUX_HEADROOM_BYTES && thermalSafe && batterySafe
        val online = snapshot.internetValidated
        if (mode == NexusMode.OFFLINE) {
            return when {
                localSafe -> NexusRouteDecision(NexusRoute.LOCAL, task, "Offline mode: use the installed local text model; internet is never used.")
                termuxSafe -> NexusRouteDecision(NexusRoute.TERMUX, task, "Offline mode: use the paired loopback Termux inference server.")
                else -> NexusRouteDecision(NexusRoute.BLOCKED, task, "Offline mode cannot complete this task: no compatible local runtime or sufficient device headroom.")
            }
        }
        if (mode == NexusMode.ONLINE) {
            return when {
                online && remoteConfigured -> NexusRouteDecision(NexusRoute.REMOTE, task, "Online mode: use the configured self-hosted remote endpoint.")
                termuxSafe -> NexusRouteDecision(NexusRoute.TERMUX, task, "Use the local Termux inference server; no internet route is required.")
                else -> NexusRouteDecision(NexusRoute.BLOCKED, task, if (!online) "No validated internet connection or reachable local inference server is available." else "No self-hosted remote model is configured.")
            }
        }
        if (localSafe) return NexusRouteDecision(NexusRoute.LOCAL, task, "Auto mode: local inference is compatible and device RAM, thermal and battery headroom are sufficient.")
        if (termuxSafe) return NexusRouteDecision(NexusRoute.TERMUX, task, "Auto mode: native app inference is unsuitable; use the paired local Termux server.")
        if (online && remoteConfigured) {
            val network = if (snapshot.wifiConnected) "Wi-Fi" else if (snapshot.cellularConnected) "mobile data" else "validated internet"
            val dataNote = if (snapshot.metered) " Metered connection detected; no model-weight download is started automatically." else ""
            return NexusRouteDecision(
                NexusRoute.REMOTE,
                task,
                "Auto mode: no safe local route is available; use the configured self-hosted endpoint over $network.$dataNote"
            )
        }
        val reason = when {
            !localCanHandle(task, snapshot.localModelPath) -> "This task needs a compatible local model or dedicated vision, image, speech or training runtime."
            !modelExists -> "No compatible local model is installed and no validated internet route is available."
            !memoryEnough -> "Phone RAM headroom is below the conservative model working-set estimate and no remote route is available."
            !thermalSafe -> "Phone temperature is too high for safe local inference and no remote route is available."
            !batterySafe -> "Battery is critically low and the phone is not charging."
            else -> "No safe execution route is available."
        }
        return NexusRouteDecision(NexusRoute.BLOCKED, task, reason)
    }
}

internal object NexusRuntimeProbe {
    fun read(context: Context, localModelPath: String, localModelLoaded: Boolean): NexusRuntimeSnapshot {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = connectivity.activeNetwork
        val capabilities = active?.let(connectivity::getNetworkCapabilities)
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return NexusRuntimeSnapshot(
            internetValidated = validated, wifiConnected = wifi, cellularConnected = cellular,
            metered = connectivity.isActiveNetworkMetered, availableRamBytes = memory.availMem,
            totalRamBytes = memory.totalMem, thermalStatus = power.currentThermalStatus,
            batteryPercent = percent, charging = charging, localModelPath = localModelPath,
            localModelLoaded = localModelLoaded
        )
    }
}
