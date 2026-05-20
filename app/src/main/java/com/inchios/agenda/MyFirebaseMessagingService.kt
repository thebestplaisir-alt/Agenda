package com.inchios.agenda

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.inchios.agendapadel.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM_RECEIVE", "Message reçu ! De: ${remoteMessage.from}")

        // 1. RÉCUPÉRATION DES DONNÉES
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Agenda Padel"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""
        val senderId = remoteMessage.data["senderId"]
        val date = remoteMessage.data["date"]

        // 2. FILTRAGE : Si c'est MOI qui ai envoyé l'action, on ne montre pas la notif
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (senderId != null && senderId == currentUserId) {
            Log.d("FCM_FILTER", "Auto-notification détectée. On ignore.")
            return
        }

        // 3. AFFICHAGE
        showNotification(title, body, date)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Nouveau token: $token")
    }

    private fun showNotification(title: String, message: String, date: String?) {
        val channelId = "agenda_matches"
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (date != null) {
                putExtra("date", date)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            System.currentTimeMillis().toInt(), 
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notifications Agenda Padel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertes pour les matchs et invitations"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
