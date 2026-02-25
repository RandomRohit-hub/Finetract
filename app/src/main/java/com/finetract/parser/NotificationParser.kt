package com.finetract.parser

import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationParser @Inject constructor() {

    fun parse(packageName: String, title: String, text: String): ParsedTransaction? {
        val combined = "$title $text".trim()

        val amount = extractAmount(combined) ?: return null
        val type = extractTransactionType(combined)
        val description = extractDescription(combined, packageName)
        val category = inferCategory(combined, packageName)

        return ParsedTransaction(
            amount = amount,
            type = type,
            description = description,
            source = packageName,
            category = category,
            rawText = combined
        )
    }

    private fun extractAmount(text: String): Double? {
        val patterns = listOf(
            Pattern.compile("(?i)(?:rs\\.?|₹|inr)\\s*([0-9,]+\\.?[0-9]*)"),
            Pattern.compile("(?i)(?:amount|amt)[:\\s]+(?:rs\\.?|₹|inr)?\\s*([0-9,]+\\.?[0-9]*)"),
            Pattern.compile("(?i)(?:debited|credited|paid|received|sent|added)\\s+(?:by|of|for)?\\s*(?:rs\\.?|₹|inr)?\\s*([0-9,]+\\.?[0-9]*)"),
            Pattern.compile("(?i)for\\s+(?:rs\\.?|₹|inr)\\s*([0-9,]+\\.?[0-9]*)"),
            Pattern.compile("(?i)(?:withdrawn|deposited)\\s*(?:rs\\.?|₹|inr)?\\s*([0-9,]+\\.?[0-9]*)"),
            Pattern.compile("([0-9,]+\\.?[0-9]*)\\s*(?:rs\\.?|₹|inr)"),
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val raw = matcher.group(1)?.replace(",", "") ?: continue
                return raw.toDoubleOrNull()
            }
        }
        return null
    }

    private fun extractTransactionType(text: String): TransactionType {
        val lower = text.lowercase()

        val debitKeywords = listOf(
            "debited", "deducted", "spent", "paid", "payment",
            "withdrawn", "sent", "transferred to", "charged",
            "purchase", "pos ", "upi txn", "txn for", "payment of"
        )
        val creditKeywords = listOf(
            "credited", "received", "added", "deposited",
            "refund", "cashback", "transferred from", "salary",
            "reversed", "returned"
        )

        val debitScore = debitKeywords.count { lower.contains(it) }
        val creditScore = creditKeywords.count { lower.contains(it) }

        // Special check for "cashback" or "refund" which often appear with "debit" in reversals
        if (lower.contains("cashback") || lower.contains("refund") || lower.contains("reversed")) {
            return TransactionType.CREDIT
        }

        return when {
            debitScore > creditScore -> TransactionType.DEBIT
            creditScore > debitScore -> TransactionType.CREDIT
            else -> TransactionType.UNKNOWN
        }
    }

    private fun extractDescription(text: String, packageName: String): String {
        val patterns = listOf(
            Pattern.compile("(?i)(?:at|to|from|towards|merchant|info)[:\\s]+([A-Za-z0-9 &'\\-]{3,30})(?:\\s|\\.|,|$)"),
            Pattern.compile("([a-zA-Z0-9._\\-]+@[a-zA-Z]+)"),
            Pattern.compile("(?i)for\\s+([A-Za-z0-9 ]{3,30})(?:\\s|\\.|,|$)"),
            Pattern.compile("(?i)Info:(?:\\s*)([A-Za-z0-9/\\- ]+)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim() ?: continue
                if (candidate.length >= 3) return candidate
            }
        }

        return friendlySourceName(packageName)
    }

    private fun inferCategory(text: String, packageName: String): String {
        val lower = text.lowercase()

        val categoryRules = mapOf(
            "Food" to listOf("swiggy", "zomato", "restaurant", "cafe", "food", "pizza", "burger", "hotel", "dining"),
            "Transport" to listOf("uber", "ola", "rapido", "metro", "fuel", "petrol", "irctc", "railway", "cab", "auto"),
            "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "mall", "store", "shop"),
            "Utilities" to listOf("electricity", "water", "gas", "bill", "recharge", "broadband", "dth", "wifi"),
            "Health" to listOf("hospital", "pharmacy", "medicine", "doctor", "clinic", "apollo", "medplus"),
            "Entertainment" to listOf("netflix", "hotstar", "spotify", "prime", "cinema", "movie", "bookmyshow"),
            "Transfer" to listOf("transfer", "sent to", "upi", "neft", "imps", "rtgs")
        )

        for ((category, keywords) in categoryRules) {
            if (keywords.any { lower.contains(it) }) return category
        }

        return "Other"
    }

    private fun friendlySourceName(packageName: String): String {
        return when (packageName) {
            "com.google.android.apps.nbu.paisa.user" -> "Google Pay"
            "net.one97.paytm" -> "Paytm"
            "com.phonepe.app" -> "PhonePe"
            "in.org.npci.upiapp" -> "BHIM"
            "com.android.mms", "com.google.android.apps.messaging",
            "com.samsung.android.messaging" -> "SMS"
            else -> "Unknown"
        }
    }
}
