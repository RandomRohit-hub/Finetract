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
    private const val KEY_HISTORY = "history_records" // JSON-like list: "date|spend|limit;..."

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    data class DailyRecord(val date: String, val spend: Float, val limit: Float)

    private const val KEY_CURRENT_STREAK = "current_streak"
    private const val KEY_BEST_STREAK = "best_streak"
    private const val KEY_LAST_CELEBRATION = "last_celebration_milestone"
    private const val KEY_STREAK_BROKEN_TODAY = "streak_broken_today"

    // Checks date and resets if it's a new day
    fun checkAndReset(context: Context) {
        val prefs = getPrefs(context)
        val lastDate = prefs.getString(KEY_LAST_RESET_DATE, "")
        val today = getTodayDate()

        if (lastDate != "" && lastDate != today) {
            // It's a new day, archive yesterday's data
            val lastSpend = prefs.getFloat(KEY_TODAY_SPEND, 0f)
            val lastLimit = prefs.getFloat(KEY_DAILY_LIMIT, 5000f)
            
            // --- Streak Evaluation ---
            val wasStreakDay = lastSpend <= lastLimit
            val currentStreak = prefs.getInt(KEY_CURRENT_STREAK, 0)
            val bestStreak = prefs.getInt(KEY_BEST_STREAK, 0)
            
            val newStreak = if (wasStreakDay) currentStreak + 1 else 0
            val newBest = if (newStreak > bestStreak) newStreak else bestStreak
            
            // --- X-Factor Reset Hook ---
            XFactorManager.onDailyReset(context, lastSpend, lastLimit)
            
            // Append to history
            val history = prefs.getString(KEY_HISTORY, "") ?: ""
            val newRecord = "$lastDate|$lastSpend|$lastLimit"
            val newHistory = if (history.isEmpty()) newRecord else "$history;$newRecord"

            prefs.edit()
                .putString(KEY_LAST_RESET_DATE, today)
                .putFloat(KEY_TODAY_SPEND, 0f)
                .putStringSet(KEY_PROCESSED_TXNS, emptySet())
                .putInt(KEY_OVER_LIMIT_COUNT, 0)
                .putString(KEY_HISTORY, newHistory)
                .putInt(KEY_CURRENT_STREAK, newStreak)
                .putInt(KEY_BEST_STREAK, newBest)
                .putBoolean(KEY_STREAK_BROKEN_TODAY, !wasStreakDay)
                .apply()
                
            // Reset notification flags for new day
            BudgetNotificationHelper.resetDailyFlags(context)
        } else if (lastDate == "") {
             // First run initialization
             prefs.edit().putString(KEY_LAST_RESET_DATE, today).apply()
        }
    }
    
    fun getHistory(context: Context): List<DailyRecord> {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        
        return raw.split(";").mapNotNull { 
            val parts = it.split("|")
            if (parts.size == 3) {
                DailyRecord(parts[0], parts[1].toFloatOrNull() ?: 0f, parts[2].toFloatOrNull() ?: 0f)
            } else null
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

    private const val KEY_IS_PRIVACY_MODE = "is_privacy_mode"

    fun isPrivacyMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_PRIVACY_MODE, false)
    }

    fun setPrivacyMode(context: Context, isPrivate: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_PRIVACY_MODE, isPrivate).apply()
    }

    // --- Intelligence Helpers ---
    fun getYesterdaySpend(context: Context): Float {
        val history = getHistory(context)
        if (history.isEmpty()) return 0f
        return history.last().spend
    }

    fun getDailyAverage(context: Context): Float {
        val history = getHistory(context)
        if (history.isEmpty()) return 0f
        var sum = 0f
        for (record in history) {
            sum += record.spend
        }
        return sum / history.size
    }

    // --- Streak Helpers ---
    fun getCurrentStreak(context: Context): Int {
        return getPrefs(context).getInt(KEY_CURRENT_STREAK, 0)
    }

    fun getBestStreak(context: Context): Int {
        return getPrefs(context).getInt(KEY_BEST_STREAK, 0)
    }

    fun wasStreakBrokenToday(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_STREAK_BROKEN_TODAY, false)
    }

    fun getStreakCelebrationMessage(context: Context): String? {
        val streak = getCurrentStreak(context)
        val lastCelebrated = getPrefs(context).getInt(KEY_LAST_CELEBRATION, 0)
        
        val milestone = when {
            streak >= 30 && lastCelebrated < 30 -> 30
            streak >= 14 && lastCelebrated < 14 -> 14
            streak >= 7 && lastCelebrated < 7 -> 7
            streak >= 3 && lastCelebrated < 3 -> 3
            else -> null
        }
        
        if (milestone != null) {
            getPrefs(context).edit().putInt(KEY_LAST_CELEBRATION, milestone).apply()
            return when (milestone) {
                3 -> "Good start! Keep it going."
                7 -> "🎉 One week of discipline!"
                14 -> "🔥 Two weeks strong!"
                30 -> "🏆 Serious control. Well done."
                else -> null
            }
        }
        return null
    }

    fun clearStreakBrokenFlag(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_STREAK_BROKEN_TODAY, false).apply()
    }

    // --- Categories ---
    object Category {
        const val FOOD = "Food"
        const val TRAVEL = "Travel"
        const val SHOPPING = "Shopping"
        const val ENTERTAINMENT = "Entertainment"
        const val TECH = "Tech"
        const val EDUCATION = "Education"
        const val DAIRY = "Dairy"
        const val STATIONERY = "Stationery"
        const val LOCAL = "Local / Basic Shops"
        const val OTHER = "Other"
    }

    fun classifyMerchant(merchantName: String): String {
        val lower = merchantName.lowercase(Locale.getDefault())
        
        return when {
            // Food
            lower.containsAny("zomato", "swiggy", "canteen", "restaurant", "cafe", "food", "mess", "pizza", "burger", "coffee") -> Category.FOOD
            
            // Travel
            lower.containsAny("ola", "uber", "rapido", "bus", "metro", "irctc", "railway", "flight", "auto", "cab", "ride") -> Category.TRAVEL
            
            // Education
            lower.containsAny("college", "school", "university", "fees", "tuition", "exam", "hostel", "book", "library") -> Category.EDUCATION
            
            // Stationery
            lower.containsAny("stationery", "xerox", "photocopy", "print", "notebook", "pen", "register", "paper") -> Category.STATIONERY
            
            // Dairy
            lower.containsAny("dairy", "milk", "paneer", "curd", "dahi", "butter", "amul", "mother dairy") -> Category.DAIRY
            
            // Shopping
            lower.containsAny("amazon", "flipkart", "myntra", "meesho", "store", "mart", "retail", "fashion") -> Category.SHOPPING
            
            // Entertainment
            lower.containsAny("pvr", "inox", "bookmyshow", "cinema", "movie", "netflix", "gaming", "play") -> Category.ENTERTAINMENT
            
            // Tech
            lower.containsAny("google", "apple", "microsoft", "github", "aws", "software", "tech", "digital") -> Category.TECH
            
            // Local
            lower.containsAny("general store", "kirana", "shop", "tea", "chai", "tapri", "stall", "backs", "snacks") -> Category.LOCAL
            
            else -> Category.OTHER
        }
    }
    
    // Helper extension
    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }

    private const val KEY_TRANSACTION_LOG = "transaction_log" // "timestamp|merchant|amount|category;..."

    fun getTransactions(context: Context): List<TransactionRecord> {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_TRANSACTION_LOG, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        
        return raw.split(";").mapNotNull { 
            val parts = it.split("|")
            if (parts.size >= 3) {
                // Handle backward compatibility for old format (3 parts) vs new (4 parts)
                val cat = if (parts.size >= 4) parts[3] else "Other"
                TransactionRecord(
                    parts[0].toLongOrNull() ?: 0L,
                    parts[1],
                    parts[2].toFloatOrNull() ?: 0f,
                    cat
                )
            } else null
        }
    }

    data class TodayStats(val count: Int, val maxAmount: Float, val maxCategory: String)

    fun getTodayStats(context: Context): TodayStats {
        val todayTxns = getTodayTransactions(context)
        
        if (todayTxns.isEmpty()) return TodayStats(0, 0f, "")
        
        val maxTxn = todayTxns.maxByOrNull { it.amount }
        return TodayStats(
            todayTxns.size,
            maxTxn?.amount ?: 0f,
            maxTxn?.category ?: ""
        )
    }

    fun getTodayTransactions(context: Context): List<TransactionRecord> {
        val all = getTransactions(context)
        val todayStr = getTodayDate()
        return all.filter { 
             SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp)) == todayStr 
        }
    }

    data class TransactionRecord(val timestamp: Long, val merchant: String, val amount: Float, val category: String)

    fun addTransaction(
        context: Context, 
        amount: Float, 
        uniqueId: String, 
        timestamp: Long, 
        merchant: String,
        rawContent: String
    ): Boolean {
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
        
        // 5. Categorize (Use Raw Content + Merchant Name for max hits)
        val combinedText = "$merchant $rawContent"
        val category = classifyMerchant(combinedText)
        
        // 6. Smart Merchant Name (Override generic title if specific brand found)
        val smartMerchant = extractMerchantName(combinedText) ?: merchant

        // Append to Log
        val log = prefs.getString(KEY_TRANSACTION_LOG, "") ?: ""
        val newEntry = "$timestamp|$smartMerchant|$amount|$category"
        val newLog = if (log.isEmpty()) newEntry else "$log;$newEntry"

        prefs.edit()
            .putFloat(KEY_TODAY_SPEND, newTotal)
            .putStringSet(KEY_PROCESSED_TXNS, newSet)
            .putString(KEY_TRANSACTION_LOG, newLog)
            .apply()
            
        // --- X-Factor Updates ---
        XFactorManager.updateWeekendStats(context, amount)
        XFactorManager.checkBigSpend(context, amount) // Check for warning (could trigger notification if we wanted, but logic is passive for now)

        return true
    }

    private fun extractMerchantName(text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        
        // List of known brands to extract (Capitalized for display)
        val brands = listOf(
            "Zomato", "Swiggy", "Uber", "Ola", "Rapido", "Amazon", "Flipkart", 
            "Myntra", "Meesho", "Netflix", "Spotify", "Apple", "Google", 
            "Starbucks", "McDonald's", "Dominos", "Pizza Hut", "KFC", "Subway",
            "Canteen", "Irctc", "Metro", "Dmart", "Reliance", "Jio", "Airtel", "Vi",
            "Steam", "PlayStation", "Xbox", "Dunzo", "Blinkit", "Zepto", "BigBasket",
            "Decathlon", "Nykaa", "BookMyShow", "PVR", "Inox"
        )
        
        // Return the first match found in text
        return brands.find { lower.contains(it.lowercase(Locale.getDefault())) }
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

    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
