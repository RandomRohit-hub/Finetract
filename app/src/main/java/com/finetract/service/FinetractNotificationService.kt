package com.finetract.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.finetract.BudgetNotificationHelper
import com.finetract.R
import com.finetract.TransactionManager
import com.finetract.data.repository.TransactionRepository
import com.finetract.parser.NotificationParser
import com.finetract.parser.TransactionType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class FinetractNotificationService : NotificationListenerService() {

    @Inject lateinit var repository: TransactionRepository
    @Inject lateinit var parser: NotificationParser

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val fullText = if (bigText.isNotBlank()) bigText else text

        Log.d("Finetract", "Notification from $packageName | $title | $fullText")

        if (!isRelevantSource(packageName, title, fullText)) return

        val parsed = parser.parse(packageName, title, fullText) ?: return

        Log.d("Finetract", "Parsed: amount=${parsed.amount}, type=${parsed.type}, desc=${parsed.description}")

        serviceScope.launch {
            // 1. Insert into Room DB for advanced analytics & recurring detection
            repository.insertTransactionFromNotification(parsed)

            // 2. For DEBIT transactions, also update TransactionManager (SharedPreferences)
            //    so the live UI (TodayFragment / HomeFragment) shows the transaction
            if (parsed.type == TransactionType.DEBIT) {
                val uniqueId = "${packageName}|${parsed.amount}|${parsed.timestamp}"
                val added = TransactionManager.addTransaction(
                    context    = applicationContext,
                    amount     = parsed.amount.toFloat(),
                    uniqueId   = uniqueId,
                    timestamp  = parsed.timestamp,
                    merchant   = parsed.description,
                    rawContent = parsed.rawText
                )

                if (added) {
                    Log.d("Finetract", "Transaction added: ${parsed.description} ₹${parsed.amount}")

                    // 3. Check budget thresholds and fire alert notification if needed
                    BudgetNotificationHelper.checkAndNotifyBudgetStatus(applicationContext)

                    // 4. Detect recurring pattern in SharedPreferences history
                    detectAndNotifyRecurring(applicationContext, parsed.description, parsed.amount.toFloat())
                }
            }
        }
    }

    /**
     * Checks the full transaction log for past transactions with the same merchant
     * and a similar amount (±10%). If found 2+ times in the last 90 days, fires a
     * recurring payment notification.
     */
    private fun detectAndNotifyRecurring(context: Context, merchant: String, amount: Float) {
        if (merchant.isBlank() || amount <= 0f) return

        val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
        val tolerance = amount * 0.10f // 10% tolerance

        val recent = TransactionManager.getTransactions(context).filter { txn ->
            txn.timestamp >= ninetyDaysAgo &&
            txn.merchant.equals(merchant, ignoreCase = true) &&
            Math.abs(txn.amount - amount) <= tolerance
        }

        if (recent.size >= 2) {
            Log.d("Finetract", "Recurring payment detected: $merchant ₹${amount.toInt()} (${recent.size} occurrences)")
            showRecurringNotification(context, merchant, amount, recent.size)
        }
    }

    private fun showRecurringNotification(context: Context, merchant: String, amount: Float, count: Int) {
        val channelId = "finetract_recurring"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Recurring Payments",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Alerts for detected recurring payments" }
            nm.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
        builder.setSmallIcon(R.drawable.ic_today)
        builder.setContentTitle("🔁 Recurring Payment Detected")
        builder.setContentText("$merchant — ₹${amount.toInt()} detected $count times")
        builder.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText("$merchant charges ₹${amount.toInt()} regularly. Detected $count times. Tap to review your subscriptions.")
        )
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        builder.setAutoCancel(true)
        val notification = builder.build()

        nm.notify(("recurring_$merchant").hashCode(), notification)
    }

    private fun isRelevantSource(
        packageName: String,
        title: String,
        text: String
    ): Boolean {
        val knownPackages = setOf(
            "com.google.android.apps.nbu.paisa.user",   // Google Pay
            "net.one97.paytm",                           // Paytm
            "com.phonepe.app",                           // PhonePe
            "in.org.npci.upiapp",                        // BHIM
            "com.amazon.mShop.android.shopping",         // Amazon Pay
            "com.mobikwik_new",                          // MobiKwik
            "com.freecharge.android",                    // FreeCharge
            "com.whatsapp",                              // WhatsApp (bank SMS forwards)
            "com.android.mms",                           // Default SMS
            "com.google.android.apps.messaging",         // Google Messages
            "org.thoughtcrime.securesms",                // Signal
            "com.samsung.android.messaging"              // Samsung Messages
        )

        if (packageName in knownPackages) return true

        val lowerText = text.lowercase()
        return lowerText.containsAny(
            "debited", "credited", "rs.", "inr", "₹",
            "spent", "paid", "payment", "transaction",
            "upi", "neft", "imps", "balance"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("Finetract", "NotificationListenerService connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("Finetract", "NotificationListenerService disconnected")
        requestRebind(ComponentName(this, FinetractNotificationService::class.java))
    }
}

private fun String.containsAny(vararg keywords: String): Boolean = keywords.any { this.contains(it) }
