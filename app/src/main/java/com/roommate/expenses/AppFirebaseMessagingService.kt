package com.roommate.expenses

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "RoomMate Expenses"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "You have a new notification"
        val type = message.data["type"] ?: "update"
        val id = message.data["eventId"] ?: "push-${message.messageId ?: System.currentTimeMillis()}"

        AppNotifications.record(this, id, title, body, type)
        AppNotifications.show(this, id, title, body, type)
        AppNotifications.broadcast(this)
    }

    override fun onNewToken(token: String) {
        FirebaseMessaging.getInstance().subscribeToTopic("updates")
    }
}
