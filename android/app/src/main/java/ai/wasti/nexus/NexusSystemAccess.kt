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
