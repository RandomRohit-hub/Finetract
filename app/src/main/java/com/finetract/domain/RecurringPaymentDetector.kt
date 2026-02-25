package com.finetract.domain

import com.finetract.data.local.entity.TransactionEntity
import com.finetract.data.local.dao.TransactionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringPaymentDetector @Inject constructor(
    private val transactionDao: TransactionDao
) {

    companion object {
        private const val RECURRENCE_WINDOW_DAYS = 35L   // look back 35 days
        private const val MIN_OCCURRENCES = 2             // must appear at least twice
        private const val AMOUNT_TOLERANCE = 0.05         // 5% amount variance allowed
        private const val DAY_TOLERANCE = 3               // ±3 days around same date
    }

    suspend fun analyzeAndFlag(newTransaction: TransactionEntity) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - (RECURRENCE_WINDOW_DAYS * 86_400_000L)
            val recentTransactions = transactionDao.getTransactionsSince(cutoff)

            val candidates = recentTransactions.filter { existing ->
                existing.id != newTransaction.id &&
                isSimilarAmount(existing.amount, newTransaction.amount) &&
                isSimilarDescription(existing.description, newTransaction.description) &&
                isSimilarDayOfMonth(existing.timestamp, newTransaction.timestamp)
            }

            if (candidates.size >= MIN_OCCURRENCES) {
                // Mark the new transaction as recurring
                transactionDao.updateRecurringFlag(newTransaction.id, true)

                // Also backfill — mark the matching historical ones
                candidates.forEach { candidate ->
                    transactionDao.updateRecurringFlag(candidate.id, true)
                }
            }
        }
    }

    private fun isSimilarAmount(a: Double, b: Double): Boolean {
        if (a == 0.0 || b == 0.0) return false
        val variance = Math.abs(a - b) / maxOf(a, b)
        return variance <= AMOUNT_TOLERANCE
    }

    private fun isSimilarDescription(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val aClean = a.lowercase().trim()
        val bClean = b.lowercase().trim()
        
        if (aClean == bClean) return true
        if (aClean.length > 4 && bClean.contains(aClean)) return true
        if (bClean.length > 4 && aClean.contains(bClean)) return true
        return false
    }

    private fun isSimilarDayOfMonth(timestampA: Long, timestampB: Long): Boolean {
        val calA = Calendar.getInstance().apply { timeInMillis = timestampA }
        val calB = Calendar.getInstance().apply { timeInMillis = timestampB }
        val dayA = calA.get(Calendar.DAY_OF_MONTH)
        val dayB = calB.get(Calendar.DAY_OF_MONTH)
        return Math.abs(dayA - dayB) <= DAY_TOLERANCE
    }
}
