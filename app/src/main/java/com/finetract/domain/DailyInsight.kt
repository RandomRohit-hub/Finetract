package com.finetract.domain

sealed class DailyInsight {
    data class OverLimit(val amount: Float) : DailyInsight()
    data class NearLimit(val remaining: Float) : DailyInsight()
    data class RecurringDetected(val merchant: String) : DailyInsight()
    data class PositiveStreak(val days: Int) : DailyInsight()
    object NoTransactions : DailyInsight()
    object None : DailyInsight()
}
