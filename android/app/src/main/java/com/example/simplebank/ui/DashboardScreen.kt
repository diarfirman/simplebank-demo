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

private val accounts = listOf("account_001", "account_002")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val scope = rememberCoroutineScope()
    var accountId by remember { mutableStateOf(accounts[0]) }
    var balance by remember { mutableStateOf<BalanceResponse?>(null) }
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var isSimulatingError by remember { mutableStateOf(false) }

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

    fun simulateError() {
        scope.launch {
            isSimulatingError = true
            try {
                // Intentional call to non-existent endpoint — generates error span in Kibana
                RetrofitClient.instance.simulateError()
            } catch (_: Exception) {
                // Expected: recorded as error trace by EDOT
            } finally {
                isSimulatingError = false
            }
        }
    }

    // Reset data and reload whenever account switches
    LaunchedEffect(accountId) {
        balance = null
        transactions = emptyList()
        loadData()
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = ::loadData,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Account switcher
            item {
                Text("Akun", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    accounts.forEachIndexed { index, id ->
                        SegmentedButton(
                            selected = accountId == id,
                            onClick = { accountId = id },
                            shape = SegmentedButtonDefaults.itemShape(index, accounts.size),
                            label = { Text(id, fontSize = 12.sp) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Error banner
            errorMessage?.let { msg ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Text(
                            text = "⚠ $msg",
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFB71C1C),
                        )
                    }
                }
            }

            // Balance card
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Saldo Rekening", fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isLoading && balance == null) {
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
            }

            // Action buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { showTransferDialog = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("Transfer") }

                    OutlinedButton(
                        onClick = ::simulateError,
                        enabled = !isSimulatingError,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFD32F2F),
                        ),
                    ) {
                        if (isSimulatingError) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFD32F2F),
                            )
                        } else {
                            Text("Simulate Error")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Transaction list
            item {
                Text("Riwayat Transaksi", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (transactions.isEmpty() && !isLoading) {
                item { Text("Belum ada transaksi.", color = Color.Gray) }
            } else {
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
