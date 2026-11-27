package ai.wasti.nexus

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

internal data class NexusReminder(val id: Int, val title: String, val triggerAtMillis: Long)

internal object NexusReminderPolicy {
    fun validate(title: String, triggerAtMillis: Long, nowMillis: Long): String? = when {
        title.isBlank() -> "Enter a reminder title."
        title.trim().length > 120 -> "Reminder titles must be 120 characters or fewer."
        triggerAtMillis <= nowMillis -> "Choose a time in the future."
        triggerAtMillis - nowMillis > 365L * 24 * 60 * 60 * 1000 -> "Choose a date within the next year."
        else -> null
    }
}

internal object NexusReminderStore {
    private const val PREFS = "nexus-reminders"
    private const val KEY_ITEMS = "items"
    const val CHANNEL_ID = "nexus-reminders"

    fun load(context: Context): List<NexusReminder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]") ?: "[]"
        val array = try { JSONArray(raw) } catch (failure: Exception) {
            throw IllegalStateException("Saved reminder data is unreadable. It has been left untouched; do not clear app data.", failure)
        }
        return (0 until array.length()).map { index ->
            val item = array.optJSONObject(index)
                ?: throw IllegalStateException("Saved reminder entry $index is invalid. Existing data has been left untouched.")
            val id = item.optInt("id", -1)
            val title = item.optString("title", "").trim()
            val time = item.optLong("triggerAtMillis", 0L)
            require(id > 0 && title.isNotBlank() && time > 0L) {
                "Saved reminder entry $index is incomplete. Existing data has been left untouched."
            }
            NexusReminder(id, title, time)
        }.distinctBy { it.id }.sortedBy { it.triggerAtMillis }
    }

    fun save(context: Context, reminders: List<NexusReminder>) {
        val array = JSONArray()
        reminders.forEach { reminder ->
            array.put(JSONObject().put("id", reminder.id).put("title", reminder.title).put("triggerAtMillis", reminder.triggerAtMillis))
        }
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).commit()) {
            "Could not save reminders to local storage."
        }
    }

    fun schedule(context: Context, reminder: NexusReminder): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        val intent = Intent(context, NexusReminderReceiver::class.java)
            .setAction("ai.wasti.nexus.action.SHOW_REMINDER")
            .setData(android.net.Uri.parse("nexus-reminder://${reminder.id}"))
            .putExtra("reminder_id", reminder.id)
            .putExtra("reminder_title", reminder.title)
        val pending = PendingIntent.getBroadcast(
            context, reminder.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending)
            true
        }.getOrDefault(false)
    }

    fun cancel(context: Context, reminder: NexusReminder) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, NexusReminderReceiver::class.java)
            .setAction("ai.wasti.nexus.action.SHOW_REMINDER")
            .setData(android.net.Uri.parse("nexus-reminder://${reminder.id}"))
        val pending = PendingIntent.getBroadcast(
            context, reminder.id, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        manager.cancel(pending)
        pending.cancel()
    }

    fun restore(context: Context) {
        val now = System.currentTimeMillis()
        load(context).filter { it.triggerAtMillis > now }.forEach { schedule(context, it) }
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "NEXUS reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Notifications for reminders you create in WASTI NEXUS." }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}

class NexusReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                runCatching { NexusReminderStore.restore(context) }
                return
            }
        }
        if (intent.action != "ai.wasti.nexus.action.SHOW_REMINDER") return
        val id = intent.getIntExtra("reminder_id", 0)
        val title = intent.getStringExtra("reminder_title")?.take(120).orEmpty()
        if (id <= 0 || title.isBlank()) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        NexusReminderStore.createNotificationChannel(context)
        val openApp = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, NexusReminderStore.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(context)
        }.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("WASTI NEXUS reminder")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }
}

class NexusReminderActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionMessage = if (granted) "Notifications enabled." else "Reminder saved, but Android notification permission is off. Enable it in App permissions to receive alerts."
    }
    private var permissionMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NexusReminderStore.createNotificationChannel(this)
        setContent {
            val reminders = remember { mutableStateListOf<NexusReminder>() }
            var title by remember { mutableStateOf("") }
            var selectedAt by remember {
                val c = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                mutableStateOf(c.timeInMillis)
            }
            var error by remember { mutableStateOf("") }
            var showDelete by remember { mutableStateOf<NexusReminder?>(null) }
            LaunchedEffect(Unit) {
                reminders.clear()
                runCatching {
                    reminders.addAll(NexusReminderStore.load(this@NexusReminderActivity))
                    NexusReminderStore.restore(this@NexusReminderActivity)
                }.onFailure { error = it.message ?: "Could not load saved reminders." }
            }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Your reminders", style = MaterialTheme.typography.headlineMedium)
                        Text("Reminders are stored on this phone. Android may delay inexact alarms to save battery; this is not an exact-alarm clock.")
                        OutlinedTextField(
                            value = title, onValueChange = { title = it.take(121) },
                            label = { Text("What should I remind you?") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        val dateText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(selectedAt))
                        OutlinedButton(onClick = {
                            val initial = Calendar.getInstance().apply { timeInMillis = selectedAt }
                            android.app.DatePickerDialog(
                                this@NexusReminderActivity,
                                { _: DatePicker, year: Int, month: Int, day: Int ->
                                    val picked = Calendar.getInstance().apply {
                                        timeInMillis = selectedAt
                                        set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
                                    }
                                    android.app.TimePickerDialog(
                                        this@NexusReminderActivity,
                                        { _: TimePicker, hour: Int, minute: Int ->
                                            picked.set(Calendar.HOUR_OF_DAY, hour); picked.set(Calendar.MINUTE, minute)
                                            picked.set(Calendar.SECOND, 0); picked.set(Calendar.MILLISECOND, 0)
                                            selectedAt = picked.timeInMillis
                                        },
                                        picked.get(Calendar.HOUR_OF_DAY), picked.get(Calendar.MINUTE), false
                                    ).show()
                                },
                                initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Choose date & time: $dateText") }
                        Button(onClick = {
                            val problem = NexusReminderPolicy.validate(title, selectedAt, System.currentTimeMillis())
                            if (problem != null) {
                                error = problem
                            } else {
                                val reminder = NexusReminder(
                                    NexusReminderParser.nextId(reminders.toList(), System.currentTimeMillis()),
                                    title.trim(), selectedAt
                                )
                                val updated = (reminders.filterNot { it.id == reminder.id } + reminder).sortedBy { it.triggerAtMillis }
                                runCatching {
                                    NexusReminderStore.save(this@NexusReminderActivity, updated)
                                    check(NexusReminderStore.schedule(this@NexusReminderActivity, reminder)) { "Android could not schedule this reminder." }
                                    reminders.clear(); reminders.addAll(updated)
                                    title = ""; error = "Reminder saved on this phone."
                                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.onFailure { error = it.message ?: "Could not save reminder." }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Save reminder") }
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.primary)
                        if (permissionMessage.isNotBlank()) Text(permissionMessage, style = MaterialTheme.typography.bodySmall)
                        Text("UPCOMING", style = MaterialTheme.typography.titleMedium)
                        if (reminders.isEmpty()) Text("No reminders yet.")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(reminders, key = { it.id }) { reminder ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(reminder.title, style = MaterialTheme.typography.titleMedium)
                                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(reminder.triggerAtMillis)))
                                        }
                                        TextButton(onClick = { showDelete = reminder }) { Text("Delete") }
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Back to chat") }
                    }
                }
            }
            if (showDelete != null) {
                val item = requireNotNull(showDelete)
                AlertDialog(
                    onDismissRequest = { showDelete = null },
                    title = { Text("Delete reminder?") },
                    text = { Text("Delete “${item.title}” from this phone?") },
                    confirmButton = {
                        TextButton(onClick = {
                            runCatching {
                                NexusReminderStore.cancel(this@NexusReminderActivity, item)
                                val updated = reminders.filterNot { it.id == item.id }
                                NexusReminderStore.save(this@NexusReminderActivity, updated)
                                reminders.clear(); reminders.addAll(updated)
                            }.onFailure { error = it.message ?: "Could not delete reminder." }
                            showDelete = null
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { showDelete = null }) { Text("Cancel") } }
                )
            }
        }
    }
}


internal data class NexusReminderDraft(val title: String, val triggerAtMillis: Long)

internal object NexusReminderParser {
    private val relative = Regex(
        "^\\s*remind me to (.{1,120}?) in (\\d{1,3}) (minutes?|mins?|hours?|hrs?|days?)\\s*[.!]?\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val tomorrow = Regex(
        "^\\s*remind me to (.{1,120}?) tomorrow at (\\d{1,2})(?::([0-5]\\d))?\\s*(am|pm)\\s*[.!]?\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val today = Regex(
        "^\\s*remind me to (.{1,120}?) today at (\\d{1,2})(?::([0-5]\\d))?\\s*(am|pm)\\s*[.!]?\\s*$",
        RegexOption.IGNORE_CASE
    )

    fun parse(prompt: String, nowMillis: Long): NexusReminderDraft? {
        val relativeMatch = relative.matchEntire(prompt)
        if (relativeMatch != null) {
            val amount = relativeMatch.groupValues[2].toLongOrNull() ?: return null
            if (amount !in 1..365) return null
            val unit = relativeMatch.groupValues[3].lowercase()
            val duration = when {
                unit.startsWith("min") -> java.time.Duration.ofMinutes(amount)
                unit.startsWith("h") -> java.time.Duration.ofHours(amount)
                else -> java.time.Duration.ofDays(amount)
            }
            val title = relativeMatch.groupValues[1].trim()
            val target = nowMillis + duration.toMillis()
            return NexusReminderDraft(title, target).takeIf {
                NexusReminderPolicy.validate(title, target, nowMillis) == null
            }
        }
        val tomorrowMatch = tomorrow.matchEntire(prompt)
        val todayMatch = today.matchEntire(prompt)
        val match = tomorrowMatch ?: todayMatch ?: return null
        val title = match.groupValues[1].trim()
        val rawHour = match.groupValues[2].toIntOrNull() ?: return null
        val minute = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        val meridiem = match.groupValues[4].lowercase()
        if (rawHour !in 1..12 || minute !in 0..59) return null
        val hour = (rawHour % 12) + if (meridiem == "pm") 12 else 0
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
        val date = if (tomorrowMatch != null) now.toLocalDate().plusDays(1) else now.toLocalDate()
        val target = java.time.LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()
        return NexusReminderDraft(title, target).takeIf {
            NexusReminderPolicy.validate(title, target, nowMillis) == null
        }
    }

    fun nextId(existing: List<NexusReminder>, nowMillis: Long): Int {
        val used = existing.mapTo(HashSet()) { it.id }
        var candidate = (nowMillis % Int.MAX_VALUE).toInt().coerceAtLeast(1)
        while (candidate in used) candidate = if (candidate == Int.MAX_VALUE) 1 else candidate + 1
        return candidate
    }
}
