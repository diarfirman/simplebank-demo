package com.example.simplebank.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.simplebank.data.RetrofitClient
import com.example.simplebank.data.models.TransferRequest
import kotlinx.coroutines.launch

@Composable
fun TransferDialog(
    fromAccountId: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var toAccountId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Transfer Dana") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = toAccountId,
                    onValueChange = { toAccountId = it },
                    label = { Text("Account ID Tujuan") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Jumlah (IDR)") },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let {
                    Text(text = it, color = Color(0xFFD32F2F))
                }
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading && toAccountId.isNotBlank() && amount.isNotBlank(),
                onClick = {
                    val amountDouble = amount.toDoubleOrNull()
                    if (amountDouble == null || amountDouble <= 0) {
                        errorMessage = "Masukkan jumlah yang valid"
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            // HTTP span auto-captured by EDOT OkHttp instrumentation plugin
                            val response = RetrofitClient.instance.transfer(
                                TransferRequest(
                                    fromAccountId = fromAccountId,
                                    toAccountId = toAccountId,
                                    amount = amountDouble,
                                )
                            )
                            if (response.isSuccessful) {
                                onSuccess()
                            } else {
                                errorMessage = when (response.code()) {
                                    422 -> "Saldo tidak mencukupi"
                                    404 -> "Rekening tujuan tidak ditemukan"
                                    else -> "Transfer gagal (${response.code()})"
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = "Tidak dapat terhubung ke server"
                        } finally {
                            isLoading = false
                        }
                    }
                },
            ) { Text("Kirim") }
        },
        dismissButton = {
            TextButton(enabled = !isLoading, onClick = onDismiss) { Text("Batal") }
        },
    )
}
