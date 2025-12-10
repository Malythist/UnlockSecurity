package com.malithyst.mysecurity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MySecurityService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "my_security_channel_foreground_v3"
        const val NOTIFICATION_ID = 1
        private const val TAG = "MySecurityService"
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                Log.d(TAG, "User unlocked device (ACTION_USER_PRESENT)")
                onUserUnlocked()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")

        createNotificationChannel()
        val notification = buildNotification()
        Log.d(TAG, "Calling startForeground()")
        startForeground(NOTIFICATION_ID, notification)

        registerUnlockReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand(): startId=$startId, intent=$intent")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Receiver not registered", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerUnlockReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(unlockReceiver, filter)
        Log.d(TAG, "unlockReceiver registered")
    }

    private fun onUserUnlocked() {
        Log.d(TAG, "onUserUnlocked()")

        // Для наглядности: обновим уведомление временем последней разблокировки
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date())

        val updatedNotification = buildNotification(
            subtitle = "Last unlock: $time"
        )

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, updatedNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MySecurity foreground",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Foreground service of MySecurity app"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created (id=$NOTIFICATION_CHANNEL_ID)")
        }
    }

    private fun buildNotification(
        subtitle: String = "Foreground service is running"
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            flags
        )

        Log.d(TAG, "Building notification (subtitle=$subtitle)")
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("MySecurity is active")
            .setContentText(subtitle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }
}
