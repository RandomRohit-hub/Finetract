package com.finetract

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object BudgetNotificationHelper {
    
    private const val CHANNEL_ID_ALERTS = "budget_alerts"
    private const val CHANNEL_ID_REMINDERS = "budget_reminders"
    
    private const val NOTIFICATION_ID_BUDGET = 1001
    
    private const val PREF_NAME = "budget_notifications"
    private const val KEY_80_NOTIFIED_DATE = "notified_80_date"
    private const val KEY_EXCEEDED_NOTIFIED_DATE = "notified_exceeded_date"
    
    /**
     * Create notification channels for budget alerts
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // High priority channel for budget exceeded
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when you exceed your daily budget"
                enableVibration(true)
            }
            
            // Default priority for reminders
            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Budget Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders when approaching your daily budget limit"
            }
            
            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }
    
    /**
     * Main function to check budget status and send appropriate notification
     */
    fun checkAndNotifyBudgetStatus(context: Context) {
        val todaySpend = TransactionManager.getTodaySpend(context)
        val limit = TransactionManager.getDailyLimit(context)
        
        if (limit <= 0) return // Budget not set
        
        val percentUsed = (todaySpend / limit) * 100
        val today = TransactionManager.getTodayDate()
        
        when {
            // Budget exceeded
            todaySpend > limit && !hasNotifiedExceeded(context, today) -> {
                showBudgetExceededNotification(context, todaySpend, limit)
                markExceededNotified(context, today)
            }
            // Approaching limit (70% threshold)
            percentUsed >= 70 && percentUsed < 100 && !hasNotified80(context, today) -> {
                showBudgetReminderNotification(context, todaySpend, limit)
                mark80Notified(context, today)
            }
        }
    }
    
    /**
     * Show notification when budget is exceeded
     */
    private fun showBudgetExceededNotification(context: Context, spent: Float, limit: Float) {
        val overAmount = spent - limit
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 Budget Exceeded!")
            .setContentText("You've spent ₹${spent.toInt()} (₹${overAmount.toInt()} over your ₹${limit.toInt()} limit)")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("You've spent ₹${spent.toInt()} today, which is ₹${overAmount.toInt()} over your daily limit of ₹${limit.toInt()}. Consider reviewing your expenses."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        if (hasNotificationPermission(context)) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BUDGET, notification)
        }
    }
    
    /**
     * Show reminder notification when approaching limit
     */
    private fun showBudgetReminderNotification(context: Context, spent: Float, limit: Float) {
        val remaining = limit - spent
        val percentUsed = ((spent / limit) * 100).toInt()
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⚠️ Budget Reminder")
            .setContentText("₹${remaining.toInt()} left before crossing your ₹${limit.toInt()} budget")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("You've used $percentUsed% of your daily budget. You have ₹${remaining.toInt()} remaining out of ₹${limit.toInt()}."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        if (hasNotificationPermission(context)) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BUDGET, notification)
        }
    }
    
    /**
     * Check if notification permission is granted
     */
    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // No runtime permission needed before Android 13
        }
    }
    
    // --- Notification State Tracking ---
    
    private fun getPrefs(context: Context) = 
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    private fun hasNotified80(context: Context, today: String): Boolean {
        return getPrefs(context).getString(KEY_80_NOTIFIED_DATE, "") == today
    }
    
    private fun mark80Notified(context: Context, today: String) {
        getPrefs(context).edit().putString(KEY_80_NOTIFIED_DATE, today).apply()
    }
    
    private fun hasNotifiedExceeded(context: Context, today: String): Boolean {
        return getPrefs(context).getString(KEY_EXCEEDED_NOTIFIED_DATE, "") == today
    }
    
    private fun markExceededNotified(context: Context, today: String) {
        getPrefs(context).edit().putString(KEY_EXCEEDED_NOTIFIED_DATE, today).apply()
    }
    
    /**
     * General purpose notification helper
     */
    fun showNotification(context: Context, id: Int, title: String, message: String) {
        val channelId = if (title.contains("Exceeded", true)) CHANNEL_ID_ALERTS else CHANNEL_ID_REMINDERS
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            id, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(if (channelId == CHANNEL_ID_ALERTS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        if (hasNotificationPermission(context)) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    /**
     * Reset notification flags (called on new day)
     */
    fun resetDailyFlags(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_80_NOTIFIED_DATE)
            .remove(KEY_EXCEEDED_NOTIFIED_DATE)
            .apply()
    }
}
