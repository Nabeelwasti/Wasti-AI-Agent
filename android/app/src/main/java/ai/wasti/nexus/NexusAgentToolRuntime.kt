package ai.wasti.nexus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

internal data class NexusAgentToolDraft(
    val id: String,
    val name: String,
    val description: String,
    val arguments: JSONObject
)

internal object NexusAgentToolRuntime {
    private val names = setOf(
        "nexus_read_screen", "nexus_read_notifications", "nexus_dismiss_notifications",
        "nexus_search_contacts", "nexus_search_shared_files", "nexus_create_reminder",
        "nexus_click_visible_control", "nexus_scroll", "nexus_navigate"
    )

    fun definitions(): JSONArray {
        val specs = listOf(
            Triple("nexus_read_screen", "Read visible screen labels. The user must approve sending the captured text to the configured online model.", emptySchema()),
            Triple("nexus_read_notifications", "Read up to 25 active notifications. The user must approve sending notification content to the configured online model.", emptySchema()),
            Triple("nexus_dismiss_notifications", "Dismiss up to 25 active non-ongoing notifications after user approval.", emptySchema()),
            Triple("nexus_search_contacts", "Search contact names only. Phone numbers are never returned to the model.", schema("query" to "string")),
            Triple("nexus_search_shared_files", "Search user-visible shared storage and return bounded excerpts. Requires the separate online file-sharing setting and per-call approval.", schema("query" to "string")),
            Triple("nexus_create_reminder", "Create a one-shot local reminder. Use ISO-8601 with an explicit UTC offset for the trigger time.", schema("title" to "string", "trigger_at" to "string")),
            Triple("nexus_click_visible_control", "Click one unique, exact visible control label. This may change the foreground app; each click requires approval.", schema("label" to "string")),
            Triple("nexus_scroll", "Scroll the current screen forward or backward.", schema("direction" to "string")),
            Triple("nexus_navigate", "Perform one Android global navigation action: back, home or notifications.", schema("action" to "string"))
        )
        return JSONArray().apply {
            specs.forEach { (name, description, parameters) ->
                put(JSONObject().put("type", "function").put("function",
                    JSONObject().put("name", name).put("description", description).put("parameters", parameters)))
            }
        }
    }

    fun parse(call: JSONObject): NexusAgentToolDraft {
        val id = call.optString("id")
        require(id.matches(Regex("^[A-Za-z0-9_-]{1,128}$"))) { "The model returned an invalid tool-call ID." }
        val function = call.optJSONObject("function") ?: error("The model returned an invalid function call.")
        val name = function.optString("name")
        require(name in names) { "The requested agent tool is not supported." }
        val raw = function.optString("arguments")
        require(raw.length <= 4096) { "Tool arguments are too large." }
        val args = JSONObject(raw)
        val allowed = when (name) {
            "nexus_search_contacts", "nexus_search_shared_files" -> setOf("query")
            "nexus_create_reminder" -> setOf("title", "trigger_at")
            "nexus_click_visible_control" -> setOf("label")
            "nexus_scroll" -> setOf("direction")
            "nexus_navigate" -> setOf("action")
            else -> emptySet()
        }
        val keys = args.keys().asSequence().toSet()
        require(keys == allowed) { "The tool supplied missing or unexpected arguments." }
        val description = when (name) {
            "nexus_read_screen" -> "Read visible screen text and share it with the configured online model."
            "nexus_read_notifications" -> "Read active notifications and share their content with the configured online model."
            "nexus_dismiss_notifications" -> "Dismiss up to 25 active non-ongoing notifications."
            "nexus_search_contacts" -> "Search contact names for “${args.getString("query").take(80)}”. Phone numbers will not be shared."
            "nexus_search_shared_files" -> "Search shared storage for “${args.getString("query").take(100)}” and share matching excerpts with the configured online model."
            "nexus_create_reminder" -> "Create reminder “${args.getString("title").take(120)}” at ${args.getString("trigger_at").take(40)}."
            "nexus_click_visible_control" -> "Click the exact visible control “${args.getString("label").take(60)}”."
            "nexus_scroll" -> "Scroll the current screen ${args.getString("direction")}."
            "nexus_navigate" -> "Perform Android navigation: ${args.getString("action")}."
            else -> error("Unsupported tool.")
        }
        return NexusAgentToolDraft(id, name, description, args)
    }

    suspend fun execute(context: Context, draft: NexusAgentToolDraft, allowFileContextOnline: Boolean): String =
        when (draft.name) {
            "nexus_read_screen" -> withContext(Dispatchers.Main.immediate) {
                val service = NexusAccessibilityService.current()
                    ?: return@withContext "Accessibility access is not enabled. Nothing was read."
                val nodes = service.snapshot(120).filter { it.enabled && (it.text.isNotBlank() || it.contentDescription.isNotBlank()) }
                if (nodes.isEmpty()) "No readable screen labels are exposed by the foreground app."
                else buildString {
                    append("Visible screen labels captured locally:\n")
                    nodes.take(40).forEach { node ->
                        append("• ").append(node.text.ifBlank { node.contentDescription }.replace('\n', ' ').take(180)).append('\n')
                    }
                }.take(6000)
            }
            "nexus_read_notifications" -> withContext(Dispatchers.Main.immediate) {
                val listener = NexusNotificationListenerService.current()
                    ?: return@withContext "Notification access is not enabled. Nothing was read."
                val items = listener.readActiveNotifications(25)
                if (items.isEmpty()) "No readable active notifications were found."
                else buildString {
                    items.forEach { item ->
                        append("• ").append(item.packageName).append(" · ").append(item.title).append('\n')
                        if (item.text.isNotBlank()) append(item.text).append('\n')
                    }
                }.take(6000)
            }
            "nexus_dismiss_notifications" -> withContext(Dispatchers.Main.immediate) {
                val listener = NexusNotificationListenerService.current()
                    ?: return@withContext "Notification access is not enabled. No notifications were dismissed."
                "Android received dismissal requests for ${listener.dismissActiveNotifications(25)} active notifications. Protected and ongoing notifications may remain."
            }
            "nexus_search_contacts" -> withContext(Dispatchers.IO) {
                if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
                    return@withContext "Contacts permission is not granted. No contacts were read."
                val query = draft.arguments.getString("query").trim().take(80)
                require(query.isNotBlank()) { "Enter a contact name to search." }
                val names = LinkedHashSet<String>()
                context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                    ContactsContract.Contacts.DISPLAY_NAME + " LIKE ?",
                    arrayOf("%$query%"),
                    ContactsContract.Contacts.DISPLAY_NAME + " ASC"
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    while (cursor.moveToNext() && names.size < 25) cursor.getString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(names::add)
                } ?: error("Android could not read the contacts database.")
                if (names.isEmpty()) "No matching contact names were found."
                else "Matching contact names (phone numbers withheld):\n" + names.joinToString("\n") { "• $it" }
            }
            "nexus_search_shared_files" -> withContext(Dispatchers.IO) {
                if (!allowFileContextOnline) return@withContext "Online file sharing is disabled. The user must enable the separate setting before file excerpts can be sent to an online model."
                if (!NexusFileSearch.hasAccess()) return@withContext "Shared-storage access is not granted. No files were searched."
                val query = draft.arguments.getString("query").trim().take(200)
                require(query.isNotBlank()) { "Enter a file name or subject to search." }
                NexusFileSearch.formatForModel(query, NexusFileSearch.search(context, query)).take(6000)
            }
            "nexus_create_reminder" -> withContext(Dispatchers.IO) {
                val title = draft.arguments.getString("title").trim().take(120)
                val at = OffsetDateTime.parse(draft.arguments.getString("trigger_at")).toInstant().toEpochMilli()
                val now = System.currentTimeMillis()
                val problem = NexusReminderPolicy.validate(title, at, now)
                require(problem == null) { problem ?: "Invalid reminder." }
                val existing = NexusReminderStore.load(context)
                val reminder = NexusReminder(NexusReminderParser.nextId(existing, now), title, at)
                NexusReminderStore.save(context, existing + reminder)
                check(NexusReminderStore.schedule(context, reminder)) { "Android could not schedule the reminder." }
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    "Reminder saved and scheduled locally, but Android notifications are disabled. Enable them in App permissions."
                else "Reminder saved on this phone. Android may delay its inexact alarm."
            }
            "nexus_click_visible_control" -> withContext(Dispatchers.Main.immediate) {
                val label = draft.arguments.getString("label").trim().take(60)
                val parsed = NexusDeviceActionParser.parse("tap the $label")
                val click = parsed?.action as? NexusDeviceAction.ClickLabel
                    ?: return@withContext "This visible label is not permitted for automated clicking."
                val service = NexusAccessibilityService.current()
                    ?: return@withContext "Accessibility access is not enabled. Nothing was clicked."
                if (service.clickByText(click.label, exact = true)) "Android accepted a click on the exact label. Verify the resulting screen before continuing."
                else "No unique enabled clickable control with that exact label was found. Nothing was clicked."
            }
            "nexus_scroll" -> withContext(Dispatchers.Main.immediate) {
                val service = NexusAccessibilityService.current()
                    ?: return@withContext "Accessibility access is not enabled. Nothing was scrolled."
                when (draft.arguments.getString("direction")) {
                    "forward" -> if (service.scroll(NexusAccessibilityService.ScrollDirection.FORWARD)) "Android accepted scroll forward." else "No scrollable control was found."
                    "backward" -> if (service.scroll(NexusAccessibilityService.ScrollDirection.BACKWARD)) "Android accepted scroll backward." else "No scrollable control was found."
                    else -> "Invalid scroll direction. Nothing was scrolled."
                }
            }
            "nexus_navigate" -> withContext(Dispatchers.Main.immediate) {
                val service = NexusAccessibilityService.current()
                    ?: return@withContext "Accessibility access is not enabled. Nothing was navigated."
                when (draft.arguments.getString("action")) {
                    "back" -> if (service.pressBack()) "Android accepted Back." else "Android rejected Back."
                    "home" -> if (service.pressHome()) "Android accepted Home." else "Android rejected Home."
                    "notifications" -> if (service.openNotifications()) "Android accepted opening notifications." else "Android rejected the notification action."
                    else -> "Unsupported navigation action."
                }
            }
            else -> error("Unsupported NEXUS tool.")
        }.take(6000)

    private fun emptySchema() = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject())
        .put("additionalProperties", false)

    private fun schema(vararg fields: Pair<String, String>) = JSONObject().apply {
        val properties = JSONObject()
        fields.forEach { (name, type) ->
            properties.put(name, JSONObject().put("type", type).put("maxLength", if (name == "trigger_at") 40 else 200))
        }
        put("type", "object")
        put("properties", properties)
        put("required", JSONArray(fields.map { it.first }))
        put("additionalProperties", false)
    }
}
