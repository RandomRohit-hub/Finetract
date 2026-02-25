package com.finetract.data.local

import androidx.room.*
import com.finetract.data.local.dao.TransactionDao
import com.finetract.data.local.entity.TransactionEntity

@Database(entities = [TransactionEntity::class], version = 1, exportSchema = false)
abstract class FinetractDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
