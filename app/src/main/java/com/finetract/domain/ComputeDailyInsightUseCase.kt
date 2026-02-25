package com.finetract.domain

import android.content.Context
import com.finetract.TransactionManager
import com.finetract.XFactorManager

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ComputeDailyInsightUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    operator fun invoke(): DailyInsight {
        val spend = TransactionManager.getTodaySpend(context)
        val limit = TransactionManager.getDailyLimit(context)
        
        if (limit <= 0f) return DailyInsight.None

        // 1. Over limit
        if (spend > limit) {
            return DailyInsight.OverLimit(spend - limit)
        }

        // 2. Near limit (>85%)
        val ratio = spend / limit
        if (ratio > 0.85f) {
            return DailyInsight.NearLimit(limit - spend)
        }

        // 3. Recurring payment detected (Contextual, logic from GhostHunter/TransactionManager)
        // For now, let's check if there's a "Ghost" detected today
        // (Assuming GhostHunter logic can be leveraged here)
        /* 
        val ghosts = GhostHunterManager.scanForGhosts(context)
        if (ghosts.isNotEmpty()) {
            return DailyInsight.RecurringDetected(ghosts.first().merchant)
        }
        */

        // 4. Positive streak (>=3 days under limit)
        val streak = TransactionManager.getCurrentStreak(context)
        if (streak >= 3) {
            return DailyInsight.PositiveStreak(streak)
        }

        // 5. No transactions yet
        if (spend == 0f) {
            return DailyInsight.NoTransactions
        }

        return DailyInsight.None
    }
}
