package com.finetract

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TransactionManager {
    private const val PREF_NAME = "finetract_prefs"
    private const val KEY_DAILY_LIMIT = "daily_limit"
    private const val KEY_TODAY_SPEND = "today_spend"
    private const val KEY_LAST_RESET_DATE = "last_reset_date"
    private const val KEY_PROCESSED_TXNS = "processed_txns"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // Checks date and resets if it's a new day
    fun checkAndReset(context: Context) {
        val prefs = getPrefs(context)
        val lastDate = prefs.getString(KEY_LAST_RESET_DATE, "")
        val today = getTodayDate()

        if (lastDate != today) {
            prefs.edit()
                .putString(KEY_LAST_RESET_DATE, today)
                .putFloat(KEY_TODAY_SPEND, 0f)
                .putStringSet(KEY_PROCESSED_TXNS, emptySet()) // New day, new transactions
                .putInt(KEY_OVER_LIMIT_COUNT, 0) // Reset alert counter
                .apply()
        }
    }

    fun getDailyLimit(context: Context): Float {
        return getPrefs(context).getFloat(KEY_DAILY_LIMIT, 5000f)
    }

    fun setDailyLimit(context: Context, limit: Float) {
        getPrefs(context).edit().putFloat(KEY_DAILY_LIMIT, limit).apply()
    }

    fun getTodaySpend(context: Context): Float {
        checkAndReset(context)
        return getPrefs(context).getFloat(KEY_TODAY_SPEND, 0f)
    }

    // In-memory cache for debounce (cleared on app kill, which is fine for "spam" prevention)
    private val debounceMap = mutableMapOf<String, Long>()
    private const val DEBOUNCE_WINDOW_MS = 10_000L // 10 Seconds
    private const val KEY_ALERT_TRIGGERED_DATE = "alert_triggered_date"
    private const val KEY_LARGE_PAYMENT_THRESHOLD = "large_payment_threshold"

    fun getLargePaymentThreshold(context: Context): Float {
        return getPrefs(context).getFloat(KEY_LARGE_PAYMENT_THRESHOLD, 0f) // 0f means disabled
    }

    fun setLargePaymentThreshold(context: Context, threshold: Float) {
        getPrefs(context).edit().putFloat(KEY_LARGE_PAYMENT_THRESHOLD, threshold).apply()
    }

    fun addTransaction(context: Context, amount: Float, uniqueId: String, timestamp: Long): Boolean {
        checkAndReset(context)
        
        // 1. Date Check
        if (!isSameDay(timestamp)) return false

        // 2. Exact Duplicate Check (Persistence)
        val prefs = getPrefs(context)
        val processed = prefs.getStringSet(KEY_PROCESSED_TXNS, mutableSetOf()) ?: mutableSetOf()
        if (processed.contains(uniqueId)) return false

        // 3. Time-Window Debounce (Heuristic)
        val parts = uniqueId.split("|")
        if (parts.size >= 2) {
            val debounceKey = "${parts[0]}|${parts[1]}" // Pkg + Amount
            val lastTime = debounceMap[debounceKey] ?: 0L
            
            if (kotlin.math.abs(timestamp - lastTime) < DEBOUNCE_WINDOW_MS) {
                return false
            }
            debounceMap[debounceKey] = timestamp
        }

        // 4. Large Payment Check
        val largeThreshold = getLargePaymentThreshold(context)
        val isLargePayment = largeThreshold > 0 && amount > largeThreshold

        val current = getTodaySpend(context)
        // If large payment, do NOT add to total, but still save the transaction as processed
        val newTotal = if (isLargePayment) current else current + amount 
        
        // Add ID to set
        val newSet = HashSet(processed)
        newSet.add(uniqueId)

        prefs.edit()
            .putFloat(KEY_TODAY_SPEND, newTotal)
            .putStringSet(KEY_PROCESSED_TXNS, newSet)
            .apply()

        // If it was a large payment, we return true (processed), but the service's limit check will see unchanged total.
        // We might want to let the service know it was skipped? 
        // For now, returning true implies "handled successfully".
        return true
    }

    fun hasAlertedToday(context: Context): Boolean {
        val prefs = getPrefs(context)
        val lastAlertDate = prefs.getString(KEY_ALERT_TRIGGERED_DATE, "")
        return lastAlertDate == getTodayDate()
    }

    fun setAlertedToday(context: Context) {
        getPrefs(context).edit().putString(KEY_ALERT_TRIGGERED_DATE, getTodayDate()).apply()
    }
    
    // New logic for Alternate Alerts
    private const val KEY_OVER_LIMIT_COUNT = "over_limit_count"

    fun incrementOverLimitCount(context: Context): Int {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_OVER_LIMIT_COUNT, 0)
        val newCount = current + 1
        prefs.edit().putInt(KEY_OVER_LIMIT_COUNT, newCount).apply()
        return newCount
    }

    // reset logic handles itself because we check date explicitly
    // But we need to ensure the count is also reset on new day
    // Updated checkAndReset:



    fun isLimitExceeded(context: Context): Boolean {
        return getTodaySpend(context) > getDailyLimit(context)
    }
    
    private fun isSameDay(timestamp: Long): Boolean {
        val date = Date(timestamp)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(date) == getTodayDate()
    }
}
