package com.example.simplebank.data.models

import com.google.gson.annotations.SerializedName

data class BalanceResponse(
    @SerializedName("account_id") val accountId: String,
    @SerializedName("owner_name") val ownerName: String,
    val balance: Double,
    val currency: String,
)

data class TransferRequest(
    @SerializedName("from_account_id") val fromAccountId: String,
    @SerializedName("to_account_id") val toAccountId: String,
    val amount: Double,
)

data class TransferResponse(
    @SerializedName("transaction_id") val transactionId: String,
    val status: String,
    val amount: Double,
)

data class Transaction(
    @SerializedName("transaction_id") val transactionId: String,
    @SerializedName("from_account_id") val fromAccountId: String,
    @SerializedName("to_account_id") val toAccountId: String,
    val amount: Double,
    val status: String,
    val timestamp: String,
    @SerializedName("trace_id") val traceId: String,
)

data class TransactionListResponse(
    @SerializedName("account_id") val accountId: String,
    val transactions: List<Transaction>,
)
