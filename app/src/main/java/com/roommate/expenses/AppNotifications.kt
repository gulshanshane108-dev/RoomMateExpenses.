package com.roommate.expenses

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject

data class AppEvent(
    val id: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val read: Boolean,
    val type: String
)

object AppNotifications {
    const val EVENT_UPDATED = "com.roommate.expenses.NOTIFICATIONS_UPDATED"
    private const val PREFS_NAME = "roommate_notifications"
    private const val KEY_EVENTS = "events"
    private const val CHANNEL_ID = "roommate_alerts"
    private const val MAX_EVENTS = 40

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun list(context: Context): List<AppEvent> {
        val raw = prefs(context).getString(KEY_EVENTS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                AppEvent(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    body = o.optString("body"),
                    timestamp = o.optLong("timestamp"),
                    read = o.optBoolean("read"),
                    type = o.optString("type", "activity")
                )
            }.filter { it.id.isNotBlank() }.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, events: List<AppEvent>) {
        val arr = JSONArray()
        events.take(MAX_EVENTS).forEach { event ->
            arr.put(
                JSONObject()
                    .put("id", event.id)
                    .put("title", event.title)
                    .put("body", event.body)
                    .put("timestamp", event.timestamp)
                    .put("read", event.read)
                    .put("type", event.type)
            )
        }
        prefs(context).edit().putString(KEY_EVENTS, arr.toString()).apply()
    }

    fun record(context: Context, id: String, title: String, body: String, type: String = "activity") {
        val current = list(context).toMutableList()
        if (current.any { it.id == id }) return
        current.add(0, AppEvent(id, title, body, System.currentTimeMillis(), false, type))
        save(context, current)
    }

    fun unreadCount(context: Context) = list(context).count { !it.read }

    fun markAllRead(context: Context) {
        save(context, list(context).map { it.copy(read = true) })
    }

    fun clear(context: Context) = save(context, emptyList())

    fun broadcast(context: Context) {
        context.sendBroadcast(Intent(EVENT_UPDATED).setPackage(context.packageName))
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RoomMate alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Room activity and new version alerts"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, eventId: String, title: String, body: String, type: String = "activity") {
        ensureChannel(context)
        val openApp = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("notificationType", type)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            openApp,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF6542B8.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Notification permission was denied; the in-app bell still tracks the event.
        }
    }
}
