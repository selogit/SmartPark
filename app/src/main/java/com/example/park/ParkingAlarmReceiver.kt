package com.example.park

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class ParkingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Budujemy wygląd powiadomienia
        val builder = NotificationCompat.Builder(context, "parking_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Systemowa ikona i
            .setContentTitle("Czas parkowania dobiega końca!")
            .setContentText("Twój bilet wkrótce wygaśnie. Czas wracać do auta.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // Wyświetlamy powiadomienie (ID 100)
        notificationManager.notify(100, builder.build())
    }
}