package ai.wasti.nexus

internal sealed class NexusDeviceAction {
    data class ClickLabel(val label: String) : NexusDeviceAction()
    data object ScrollForward : NexusDeviceAction()
    data object ScrollBackward : NexusDeviceAction()
    data object Back : NexusDeviceAction()
    data object Home : NexusDeviceAction()
    data object Notifications : NexusDeviceAction()
    data object ReadScreen : NexusDeviceAction()
}

internal data class NexusDeviceActionDraft(val action: NexusDeviceAction, val description: String)

internal object NexusDeviceActionParser {
    private val click = Regex("^(?:click|tap)(?: on)? (?:the )?(.{1,60}?)(?: button)?$", RegexOption.IGNORE_CASE)
    private val prohibited = listOf(
        "delete", "remove", "erase", "clear", "send", "submit", "pay", "purchase", "buy",
        "transfer", "confirm", "allow", "deny", "accept", "decline", "install", "uninstall",
        "call", "message", "post", "publish", "share", "password", "permission", "sign out", "log out"
    )

    fun parse(prompt: String): NexusDeviceActionDraft? {
        val value = prompt.trim()
        if (value.equals("scroll down", true)) return NexusDeviceActionDraft(NexusDeviceAction.ScrollForward, "Scroll the current screen down")
        if (value.equals("scroll up", true)) return NexusDeviceActionDraft(NexusDeviceAction.ScrollBackward, "Scroll the current screen up")
        if (value.equals("go back", true) || value.equals("press back", true)) return NexusDeviceActionDraft(NexusDeviceAction.Back, "Press Android Back")
        if (value.equals("go home", true) || value.equals("press home", true)) return NexusDeviceActionDraft(NexusDeviceAction.Home, "Go to the Android Home screen")
        if (value.equals("open notifications", true) || value.equals("show notifications", true)) return NexusDeviceActionDraft(NexusDeviceAction.Notifications, "Open the Android notification shade")
        if (value.equals("read current screen", true) || value.equals("show current screen text", true)) return NexusDeviceActionDraft(NexusDeviceAction.ReadScreen, "Read visible screen labels locally")
        val match = click.matchEntire(value) ?: return null
        val label = match.groupValues[1].trim().trim('"', '\'')
        if (label.isBlank() || label.length > 60 || prohibited.any { term ->
                Regex("(^|[^a-z])" + Regex.escape(term) + "([^a-z]|$)", RegexOption.IGNORE_CASE).containsMatchIn(label)
            }) return null
        return NexusDeviceActionDraft(NexusDeviceAction.ClickLabel(label), "Click the visible control labelled “$label”")
    }
}

internal object NexusDeviceActionExecutor {
    fun execute(action: NexusDeviceAction): String {
        val service = NexusAccessibilityService.current()
            ?: return "Device action not run. Enable WASTI NEXUS Device Control in Android Accessibility settings first."
        return when (action) {
            is NexusDeviceAction.ClickLabel -> {
                if (service.clickByText(action.label, exact = true)) {
                    "Android accepted a click on the exact visible label “${action.label}”. Check the screen before continuing; this action does not prove the target app completed its operation."
                } else {
                    "No enabled, clickable control with the exact visible label “${action.label}” was found. Nothing was clicked."
                }
            }
            NexusDeviceAction.ScrollForward -> if (service.scroll(NexusAccessibilityService.ScrollDirection.FORWARD)) "Android accepted a scroll-down action." else "No enabled scrollable control was found; nothing was scrolled."
            NexusDeviceAction.ScrollBackward -> if (service.scroll(NexusAccessibilityService.ScrollDirection.BACKWARD)) "Android accepted a scroll-up action." else "No enabled scrollable control was found; nothing was scrolled."
            NexusDeviceAction.Back -> if (service.pressBack()) "Android accepted the Back action." else "Android did not accept the Back action."
            NexusDeviceAction.Home -> if (service.pressHome()) "Android accepted the Home action." else "Android did not accept the Home action."
            NexusDeviceAction.Notifications -> if (service.openNotifications()) "Android accepted the notification-shade action." else "Android did not accept the notification-shade action."
            NexusDeviceAction.ReadScreen -> {
                val nodes = service.snapshot(120).filter { it.enabled && (it.text.isNotBlank() || it.contentDescription.isNotBlank()) }
                if (nodes.isEmpty()) "No readable screen labels are currently exposed. Secure screens and some apps hide their content."
                else buildString {
                    append("Screen text captured locally (not sent to an AI server):\n")
                    nodes.take(40).forEach { node ->
                        val label = node.text.ifBlank { node.contentDescription }
                        if (label.isNotBlank()) append("• ").append(label.replace('\n', ' ').take(180)).append('\n')
                    }
                    if (nodes.size > 40) append("…and ").append(nodes.size - 40).append(" more visible controls.")
                }.take(7_000)
            }
        }
    }
}
