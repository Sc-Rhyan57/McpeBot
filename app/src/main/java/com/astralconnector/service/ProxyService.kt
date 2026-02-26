package com.astralconnector.service

import android.app.*
import android.content.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.astralconnector.MainActivity

class ProxyService : Service() {
    companion object {
        const val CHANNEL_ID = "astral_proxy_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "STOP_PROXY"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() { super.onCreate(); createChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopPi = PendingIntent.getService(this, 1, Intent(this, ProxyService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Astral Connector")
            .setContentText("Proxy active — sharing server on LAN")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Proxy Service", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }
}
