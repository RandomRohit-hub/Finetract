package com.finetract.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.finetract.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getTransactionsSince(since: Long): List<TransactionEntity>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE timestamp >= :startOfDay AND type = 'DEBIT'")
    fun observeTotalSpentToday(startOfDay: Long): Flow<Double>

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity): Int

    @Query("UPDATE transactions SET isRecurring = :flag WHERE id = :id")
    suspend fun updateRecurringFlag(id: Int, flag: Boolean): Int

    @Query("DELETE FROM transactions")
    suspend fun clearAll(): Int
}
