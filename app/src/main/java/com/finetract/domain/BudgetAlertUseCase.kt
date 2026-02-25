package com.finetract.domain

import com.finetract.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

enum class BudgetAlertLevel { NONE, APPROACHING, EXCEEDED }

data class BudgetAlertState(
    val level: BudgetAlertLevel,
    val spentToday: Double,
    val dailyLimit: Double,
    val percentUsed: Double
) {
    companion object {
        val INITIAL = BudgetAlertState(BudgetAlertLevel.NONE, 0.0, 0.0, 0.0)
    }
}

class BudgetAlertUseCase @Inject constructor(
    private val transactionRepo: TransactionRepository
) {
    fun observe(dateMillis: Long, dailyLimit: Double): Flow<BudgetAlertState> {
        // We observe the DAO flow for the specific date
        return transactionRepo.observeTotalSpentToday(dateMillis).combine(flowOf(dailyLimit)) { spent, limit ->
            val spentVal = spent
            val percent = if (limit > 0) (spentVal / limit) * 100.0 else 0.0
            val level = when {
                percent >= 100.0 -> BudgetAlertLevel.EXCEEDED
                percent >= 70.0  -> BudgetAlertLevel.APPROACHING
                else             -> BudgetAlertLevel.NONE
            }
            BudgetAlertState(level, spentVal, limit, percent)
        }
    }
}
