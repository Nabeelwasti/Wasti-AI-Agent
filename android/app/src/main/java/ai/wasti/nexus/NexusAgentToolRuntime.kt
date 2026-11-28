package ai.wasti.nexus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import android.provider.ContactsContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.resume

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
        "nexus_click_visible_control", "nexus_scroll", "nexus_navigate",
        "nexus_dial_contact", "nexus_compose_sms", "nexus_open_sms_inbox", "nexus_open_call_history",
        "nexus_get_location", "nexus_health_summary"
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
            Triple("nexus_navigate", "Perform one Android global navigation action: back, home or notifications.", schema("action" to "string")),
            Triple("nexus_dial_contact", "Find one unambiguous contact and open the Android dialer with that number. The user must press Call manually.", schema("contact_name" to "string")),
            Triple("nexus_compose_sms", "Find one unambiguous contact and open the Android SMS composer with a draft. The user must review and press Send manually.", schema("contact_name" to "string", "message" to "string")),
            Triple("nexus_open_sms_inbox", "Open the system messaging app so the user can read their SMS inbox. NEXUS does not read SMS history.", emptySchema()),
            Triple("nexus_open_call_history", "Open the system dialer so the user can review Recents. NEXUS does not read call-log history.", emptySchema()),
            Triple("nexus_get_location", "Request one approximate foreground location fix. Coordinates are shared with the online model only after the user's approval.", emptySchema()),
            Triple("nexus_health_summary", "Read seven-day aggregate steps and average heart rate from Health Connect. Only a summary is shared with the online model after approval.", emptySchema())
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
            "nexus_dial_contact" -> setOf("contact_name")
            "nexus_compose_sms" -> setOf("contact_name", "message")
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
            "nexus_dial_contact" -> "Find contact “${args.getString("contact_name").take(80)}” and open the system dialer. You must tap Call manually."
            "nexus_compose_sms" -> "Open an SMS draft for “${args.getString("contact_name").take(80)}” with this message: “${args.getString("message").take(160)}”. You must review and tap Send manually."
            "nexus_open_sms_inbox" -> "Open the system Messages app. NEXUS will not read your SMS inbox."
            "nexus_open_call_history" -> "Open the system dialer. NEXUS will not read your call history."
            "nexus_get_location" -> "Request one approximate location fix and share coordinates with the selected online model."
            "nexus_health_summary" -> "Read the last seven days of steps and heart-rate summary from Health Connect and share that summary with the selected online model."
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
                check(NexusReminderStore.schedule(context, reminder)) { "Android could not schedule the reminder." }
                try {
                    NexusReminderStore.save(context, existing + reminder)
                } catch (error: Exception) {
                    NexusReminderStore.cancel(context, reminder)
                    throw error
                }
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
            "nexus_dial_contact" -> launchContactHandoff(context, draft, composeSms = false)
            "nexus_compose_sms" -> launchContactHandoff(context, draft, composeSms = true)
            "nexus_open_sms_inbox" -> withContext(Dispatchers.Main.immediate) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) == null) "No compatible messaging app was found."
                else { context.startActivity(intent); "Opened the system messaging app. Review your inbox there; NEXUS did not read SMS history." }
            }
            "nexus_open_call_history" -> withContext(Dispatchers.Main.immediate) {
                val intent = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) == null) "No compatible dialer app was found."
                else { context.startActivity(intent); "Opened the system dialer. Choose Recents to review call history; NEXUS did not read call logs." }
            }
            "nexus_get_location" -> readOneShotLocation(context)
            "nexus_health_summary" -> readHealthSummary(context)
            else -> error("Unsupported NEXUS tool.")
        }.take(6000)

    @Suppress("MissingPermission")
    private suspend fun readOneShotLocation(context: Context): String = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return@withContext "Approximate location permission is not granted. No location was accessed."
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return@withContext "Android location service is unavailable."
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return@withContext "Android Location services are disabled. No location was accessed."
        val cancellation = CancellationSignal()
        val fix = try {
            withTimeoutOrNull(25_000L) {
                suspendCancellableCoroutine<android.location.Location?> { continuation ->
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
            }
        } finally {
            cancellation.cancel()
        } ?: return@withContext "No location fix was returned within 25 seconds."
        "One-time approximate location fix: latitude ${fix.latitude}, longitude ${fix.longitude}, accuracy about ${fix.accuracy.toInt()} metres. This is a single foreground fix, not background tracking."
    }

    private suspend fun readHealthSummary(context: Context): String = withContext(Dispatchers.IO) {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE)
            return@withContext "Health Connect is unavailable or needs an update. No health data was read."
        val client = HealthConnectClient.getOrCreate(context)
        val required = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(required))
            return@withContext "Health Connect read permissions are not granted. Open Health & sensors and grant Steps and Heart Rate access before asking again."
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(7))
        val range = TimeRangeFilter.between(start, end)
        val steps = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL] ?: 0L
        val records = client.readRecords(
            ReadRecordsRequest(recordType = HeartRateRecord::class, timeRangeFilter = range, pageSize = 100)
        ).records
        val readings = records.flatMap { it.samples }.map { it.beatsPerMinute }
        val heart = if (readings.isEmpty()) "No heart-rate samples were recorded in the selected period."
        else "Average recorded heart rate: ${readings.average().toInt()} bpm from ${readings.size} samples."
        "Health Connect summary for the last 7 days: $steps steps. $heart Only this summary was read; no background health tracking was started."
    }

    private suspend fun launchContactHandoff(context: Context, draft: NexusAgentToolDraft, composeSms: Boolean): String =
        withContext(Dispatchers.IO) {
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
                return@withContext "Contacts permission is not granted. No contact was accessed."
            val name = draft.arguments.getString("contact_name").trim().take(80)
            require(name.isNotBlank()) { "Enter a contact name." }
            val matches = LinkedHashMap<String, Pair<String, String>>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$name%"),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && matches.size < 20) {
                    val displayName = cursor.getString(nameIndex).orEmpty().trim()
                    val number = cursor.getString(numberIndex).orEmpty().trim()
                    if (displayName.isNotBlank() && number.isNotBlank()) matches.putIfAbsent(displayName + "|" + number, displayName to number)
                }
            } ?: error("Android could not read the contacts database.")
            if (matches.isEmpty()) return@withContext "No matching contact with a phone number was found."
            if (matches.size != 1) return@withContext "More than one contact or phone number matched. Open Contacts and choose the correct entry manually."
            val (displayName, number) = matches.values.single()
            val intent = if (composeSms) {
                val message = draft.arguments.getString("message").trim().take(2000)
                require(message.isNotBlank()) { "The SMS draft cannot be empty." }
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
                    .putExtra("sms_body", message)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main.immediate) {
                if (intent.resolveActivity(context.packageManager) == null) {
                    if (composeSms) "No compatible SMS composer was found." else "No compatible dialer was found."
                } else {
                    context.startActivity(intent)
                    if (composeSms) "Opened the SMS composer for $displayName with a draft. Review the recipient and message, then press Send manually. NEXUS did not send anything."
                    else "Opened the dialer for $displayName. Review the number and press Call manually. NEXUS did not place a call."
                }
            }
        }

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
