package com.example.simplebank.data

import com.example.simplebank.data.models.BalanceResponse
import com.example.simplebank.data.models.TransactionListResponse
import com.example.simplebank.data.models.TransferRequest
import com.example.simplebank.data.models.TransferResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @GET("/balance/{accountId}")
    suspend fun getBalance(@Path("accountId") accountId: String): Response<BalanceResponse>

    @POST("/transfer")
    suspend fun transfer(@Body request: TransferRequest): Response<TransferResponse>

    @GET("/transactions/{accountId}")
    suspend fun getTransactions(@Path("accountId") accountId: String): Response<TransactionListResponse>

    @GET("/simulate-error")
    suspend fun simulateError(): Response<Unit>
}
