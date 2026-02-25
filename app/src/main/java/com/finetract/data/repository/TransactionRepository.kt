package com.finetract.data.repository

import com.finetract.data.local.dao.TransactionDao
import com.finetract.data.local.entity.TransactionEntity
import com.finetract.domain.RecurringPaymentDetector
import com.finetract.parser.ParsedTransaction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val recurringDetector: RecurringPaymentDetector
) {

    fun getAllTransactions(): Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    fun observeTotalSpentToday(startOfDay: Long): Flow<Double> = transactionDao.observeTotalSpentToday(startOfDay)

    suspend fun getTransactionsSince(since: Long): List<TransactionEntity> = transactionDao.getTransactionsSince(since)

    suspend fun insertTransactionFromNotification(parsed: ParsedTransaction) {
        val entity = TransactionEntity(
            amount = parsed.amount,
            category = parsed.category,
            description = parsed.description,
            timestamp = parsed.timestamp,
            isRecurring = false,
            type = parsed.type.name,
            source = parsed.source,
            rawText = parsed.rawText
        )
        val insertedId = transactionDao.insert(entity)

        // Immediately analyze for recurrence after insert
        val inserted = entity.copy(id = insertedId.toInt())
        recurringDetector.analyzeAndFlag(inserted)
    }

    suspend fun updateRecurringFlag(transactionId: Int, isRecurring: Boolean) {
        transactionDao.updateRecurringFlag(transactionId, isRecurring)
    }

    suspend fun clearAll() {
        transactionDao.clearAll()
    }
}
