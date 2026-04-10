package com.example.simplebank.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simplebank.data.RetrofitClient
import com.example.simplebank.data.models.BalanceResponse
import com.example.simplebank.data.models.Transaction
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(accountId: String = "account_001") {
    val scope = rememberCoroutineScope()
    var balance by remember { mutableStateOf<BalanceResponse?>(null) }
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }

    fun loadData() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                // HTTP spans auto-captured by EDOT OkHttp instrumentation plugin
                val balanceResp = RetrofitClient.instance.getBalance(accountId)
                val txResp = RetrofitClient.instance.getTransactions(accountId)
                if (balanceResp.isSuccessful) {
                    balance = balanceResp.body()
                } else {
                    errorMessage = "Gagal memuat saldo (${balanceResp.code()})"
                }
                if (txResp.isSuccessful) {
                    transactions = txResp.body()?.transactions ?: emptyList()
                }
            } catch (e: Exception) {
                errorMessage = "Tidak dapat terhubung ke server. Cek koneksi kamu."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(accountId) { loadData() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        errorMessage?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Text(
                    text = "⚠ $it",
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFB71C1C),
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Saldo Rekening", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = balance?.let { "Rp ${"%,.0f".format(it.balance)}" } ?: "-",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = balance?.ownerName ?: "",
                        fontSize = 14.sp,
                        color = Color.Gray,
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { showTransferDialog = true }, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Transfer")
            }
            OutlinedButton(onClick = { loadData() }, modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text("Refresh")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Riwayat Transaksi", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (transactions.isEmpty() && !isLoading) {
            Text("Belum ada transaksi.", color = Color.Gray)
        } else {
            LazyColumn {
                items(transactions) { tx ->
                    TransactionItem(tx = tx, currentAccountId = accountId)
                }
            }
        }
    }

    if (showTransferDialog) {
        TransferDialog(
            fromAccountId = accountId,
            onDismiss = { showTransferDialog = false },
            onSuccess = {
                showTransferDialog = false
                loadData()
            },
        )
    }
}

@Composable
private fun TransactionItem(tx: Transaction, currentAccountId: String) {
    val isOutgoing = tx.fromAccountId == currentAccountId
    val sign = if (isOutgoing) "-" else "+"
    val color = if (isOutgoing) Color(0xFFD32F2F) else Color(0xFF388E3C)
    val other = if (isOutgoing) tx.toAccountId else tx.fromAccountId

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(if (isOutgoing) "Kirim ke $other" else "Terima dari $other", fontSize = 14.sp)
                Text(tx.timestamp.take(10), fontSize = 12.sp, color = Color.Gray)
            }
            Text(
                text = "$sign Rp ${"%,.0f".format(tx.amount)}",
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
