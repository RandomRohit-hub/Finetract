package com.finetract

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.regex.Pattern

class FinetractNotificationService : NotificationListenerService() {

    companion object {
        const val TAG = "FinetractService"
        val LISTEN_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // GPay
            "com.phonepe.app", // PhonePe
            "net.one97.paytm", // Paytm
            "in.org.npci.upiapp", // BHIM
            "com.mand.notitest" // User's Test App
        )
        
        // Match amount with currency symbol: ₹ 100, Rs. 100, INR 100
        val AMOUNT_PATTERN = Pattern.compile("(?i)(?:₹|INR|Rs\\.?)\\s*([\\d,]+(?:\\.\\d{1,2})?)")
        
        val POSITIVE_KEYWORDS = listOf("paid", "sent", "transfer", "debited", "successful", "completed")
        val NEGATIVE_KEYWORDS = listOf("failed", "declined", "pending", "request", "reversed", "refund", "credite") // Added 'credite' for 'credited' (incoming money shouldn't count as expense usually?? User requirement says "expense", so ignore incoming)
        // User asked to track expenses. "Credited" is income. "Debited" or "Paid" is expense.
        // I will add "credited" to negative keywords just in case, unless user sends money to self? - Let's stick to explicit failure keywords first + "credited" if logic implies expense only.
        // Actually, user said "Extract Amount" but typically expense tracking implies outgoing.
        // Safe to ignore "failed", "declined", "pending". 
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Service Connected")
        DebugLogManager.log("Service Linked & Ready!")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        
        // Enhanced logging: Log ALL notifications received
        DebugLogManager.log("📨 Notif from: $packageName")
        
        if (!LISTEN_PACKAGES.contains(packageName)) {
            DebugLogManager.log("❌ Ignored: Pkg not in whitelist")
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        
        // Enhanced logging: Show raw content
        DebugLogManager.log("📄 Title: '$title'")
        DebugLogManager.log("📄 Text: '$text'")
        
        val fullText = "$title $text".lowercase(java.util.Locale.getDefault())

        // 1. Check for FAILURE keywords first
        if (NEGATIVE_KEYWORDS.any { fullText.contains(it) }) {
             DebugLogManager.log("❌ Failed/Pending keyword detected")
             return
        }

        // 2. Check for SUCCESS/TRANSACTION keywords
        val foundKeyword = POSITIVE_KEYWORDS.find { fullText.contains(it) }
        if (foundKeyword == null) {
             DebugLogManager.log("❌ No success keyword in: '$fullText'") 
             return
        }
        DebugLogManager.log("✅ Keyword '$foundKeyword' matched")

        // 3. Extract Amount
        val originalText = "$title $text"
        DebugLogManager.log("🔍 Searching for amount in: '$originalText'")
        
        val matcher = AMOUNT_PATTERN.matcher(originalText)
        if (matcher.find()) {
            try {
                val amountStr = matcher.group(1)?.replace(",", "") ?: return
                val amount = amountStr.toFloat()
                
                DebugLogManager.log("💰 Found amount: ₹$amount")
                
                // Robust Unique ID: Package + Amount + Timestamp (ms)
                // Use postTime from SBN to ensure it's tied to the event time, not processing time
                val uniqueId = "${packageName}|${amount}|${sbn.postTime}"
                
                // Use Title as Merchant Name (e.g. "Paid to Zomato")
                val merchant = title.ifEmpty { "Unknown Merchant" }
                val rawContent = "$title $text"

                val added = TransactionManager.addTransaction(this, amount, uniqueId, sbn.postTime, merchant, rawContent)
                if (added) {
                    val msg = "✅ Success: ₹$amount"
                    Log.d(TAG, msg)
                    DebugLogManager.log(msg)
                    checkLimitAndNotify()
                    // Check and send budget notifications
                    BudgetNotificationHelper.checkAndNotifyBudgetStatus(this)
                } else {
                    DebugLogManager.log("⚠️ Duplicate ignored: ₹$amount")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing amount", e)
                DebugLogManager.log("❌ Error parsing: ${e.message}")
            }
        } else {
            DebugLogManager.log("❌ No amount found in regex match")
        }
    }

    private fun checkLimitAndNotify() {
        val spend = TransactionManager.getTodaySpend(this)
        val limit = TransactionManager.getDailyLimit(this)
        
        if (spend > limit) {
             // Alternate Alert Logic: 1st, 3rd, 5th... time we cross/add to the limit
             val count = TransactionManager.incrementOverLimitCount(this)
             
             // If count is Odd (1, 3, 5...), Send Alert
             // If count is Even (2, 4, 6...), Skip
             if (count % 2 != 0) {
                 sendLimitAlert(spend, limit)
                 DebugLogManager.log("Limit exceeded! Alert #$count sent.")
             } else {
                 DebugLogManager.log("Limit exceeded (Alert #$count skipped - alternate).")
             }
        }
    }

    private fun sendLimitAlert(spend: Float, limit: Float) {
        val channelId = "budget_alerts_v2"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Daily Budget Alerts", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Immediate alerts when daily spending limit is crossed."
            channel.enableVibration(true)
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Daily Limit Crossed")
            .setContentText("You spent ₹$spend today, which exceeds your daily limit of ₹$limit.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("You spent ₹$spend today, which exceeds your daily limit of ₹$limit."))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        nm.notify(1001, notification)
        DebugLogManager.log("ALERT SENT: Over limit!")
    }
}
