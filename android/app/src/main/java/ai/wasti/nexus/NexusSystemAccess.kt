package ai.wasti.nexus

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings

/**
 * Narrow Android entry points that let the user complete sensitive operations
 * in the system or first-party app. This intentionally does not request broad
 * permissions or silently change device settings.
 */
object NexusSystemAccess {
    fun permissionSnapshot(context: Context): List<Pair<String, String>> {
        fun granted(permission: String): Boolean =
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val notificationListeners = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ).orEmpty()
        val notificationEnabled = notificationListeners.split(':').any {
            android.content.ComponentName.unflattenFromString(it)?.packageName == context.packageName
        }
        val notificationPermission = android.os.Build.VERSION.SDK_INT < 33 ||
            granted(android.Manifest.permission.POST_NOTIFICATIONS)
        val installPermission = android.os.Build.VERSION.SDK_INT < 26 ||
            context.packageManager.canRequestPackageInstalls()
        return listOf(
            "Contacts" to if (granted(android.Manifest.permission.READ_CONTACTS)) "Granted" else "Not granted",
            "Approximate location" to if (granted(android.Manifest.permission.ACCESS_COARSE_LOCATION)) "Granted" else "Not granted",
            "Activity recognition" to if (android.os.Build.VERSION.SDK_INT < 29 || granted(android.Manifest.permission.ACTIVITY_RECOGNITION)) "Granted / not required" else "Not granted",
            "Shared storage search" to if (hasAllFilesAccess()) "Granted" else "Not granted",
            "Accessibility actions" to if (NexusAccessibilityService.isEnabled(context)) "Enabled" else "Disabled",
            "Notification access" to if (notificationEnabled) "Enabled" else "Disabled",
            "Post notifications" to if (notificationPermission) "Allowed" else "Denied",
            "Install unknown apps" to if (installPermission) "Allowed" else "Not allowed",
            "Health Connect" to "Manage separately in Health & sensors",
            "Background location" to "Not requested",
            "SMS and call logs" to "Not requested; Android restricted access applies",
            "Device administrator / owner" to "Not requested"
        )
    }

    fun openAccessibilitySettings(context: Context) =
        launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppDetails(context: Context) =
        launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

    fun hasAllFilesAccess(): Boolean =
        android.os.Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager()

    fun openAllFilesAccess(context: Context): Boolean {
        val appIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        return if (appIntent.resolveActivity(context.packageManager) != null) {
            launch(context, appIntent)
        } else {
            launch(context, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    fun openOverlayAccess(context: Context) =
        launch(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))

    fun openNotificationListenerSettings(context: Context) =
        launch(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openUsageAccessSettings(context: Context) =
        launch(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openBatterySettings(context: Context) =
        launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun openWifiSettings(context: Context) =
        launch(context, Intent(Settings.ACTION_WIFI_SETTINGS))

    fun openLocationSettings(context: Context) =
        launch(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    fun createAlarm(context: Context) =
        launch(context, Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_SKIP_UI, false))

    fun createCalendarEvent(context: Context) =
        launch(context, Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI))

    fun openContacts(context: Context) =
        launch(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS))

    fun openCalendar(context: Context) =
        launch(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))

    fun openClock(context: Context) =
        launch(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    private fun launch(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (intent.resolveActivity(context.packageManager) == null) false
            else {
                context.startActivity(intent)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
