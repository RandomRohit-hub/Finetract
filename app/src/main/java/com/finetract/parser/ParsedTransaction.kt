package com.finetract.parser

enum class TransactionType { DEBIT, CREDIT, UNKNOWN }

data class ParsedTransaction(
    val amount: Double,
    val type: TransactionType,
    val description: String,
    val source: String,
    val category: String,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis()
)
