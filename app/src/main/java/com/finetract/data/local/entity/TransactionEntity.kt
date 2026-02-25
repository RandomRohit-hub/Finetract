package com.finetract.data.local.entity

import androidx.room.*

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val category: String,
    val description: String,
    val timestamp: Long,
    val isRecurring: Boolean = false,
    val type: String, // DEBIT, CREDIT
    val source: String? = null,
    val rawText: String? = null
)
