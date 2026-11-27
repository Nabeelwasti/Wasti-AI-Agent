package ai.wasti.nexus

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

internal data class NexusVisibleNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long
)

/**
 * User-enabled notification bridge. It does not cache, persist, forward or
 * react to posted notifications. Content is read only on an explicit request.
 */
class NexusNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        active = this
    }

    override fun onListenerDisconnected() {
        if (active === this) active = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    internal fun readActiveNotifications(limit: Int = 25): List<NexusVisibleNotification> {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            "Notification snapshots must be requested on the Android main thread."
        }
        val notifications = activeNotifications ?: return emptyList()
        return notifications.asSequence()
            .filterNot { it.packageName == packageName }
            .filterNot { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
            .sortedByDescending(StatusBarNotification::getPostTime)
            .take(limit.coerceIn(1, 25))
            .mapNotNull { sbn ->
                val extras = sbn.notification.extras ?: return@mapNotNull null
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(180)
                val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty().take(500)
                if (title.isBlank() && text.isBlank()) null
                else NexusVisibleNotification(sbn.packageName.take(120), title, text, sbn.postTime)
            }.toList()
    }

    companion object {
        @Volatile private var active: NexusNotificationListenerService? = null

        fun current(): NexusNotificationListenerService? = active

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, NexusNotificationListenerService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
