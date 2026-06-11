package com.silica.assistant.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.auth.AuthRepository
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(onAuthSuccess: () -> Unit, onBack: () -> Unit) {
    val authRepository: AuthRepository = koinInject()
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isRegister by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isRegister) "Daftar Akun Silica" else "Login Silica",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Espresso
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Simpan progress waifu kamu di cloud",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it.trim().replace("\n", "") },
            label = { Text("Username atau Email") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepRose,
                focusedLabelColor = DeepRose
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it.trim().replace("\n", "") },
            label = { Text("Password") },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isPasswordVisible) "Sembunyikan password" else "Tampilkan password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepRose,
                focusedLabelColor = DeepRose
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (message.isNotEmpty()) {
            Text(
                text = message, 
                color = if (message.contains("berhasil", true)) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, 
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    message = "Harap isi username dan password!"
                    return@Button
                }
                if (password.length < 6) {
                    message = "Password minimal 6 karakter!"
                    return@Button
                }
                isLoading = true
                scope.launch {
                    val result = if (isRegister) {
                        authRepository.register(username, password)
                    } else {
                        authRepository.login(username, password)
                    }
                    
                    isLoading = false
                    result.onSuccess { res ->
                        message = res.message
                        if (res.success && !isRegister) {
                            scope.launch {
                                message = "Mengunduh progress dari cloud..."
                                authRepository.syncPull()
                                onAuthSuccess()
                            }
                        } else if (res.success && isRegister) {
                            isRegister = false
                            message = "Pendaftaran berhasil! Silakan login."
                        }
                    }.onFailure {
                        message = when {
                            it.message?.contains("network", true) == true -> "Masalah koneksi internet."
                            it.message?.contains("password", true) == true -> "Password salah atau terlalu lemah."
                            it.message?.contains("user-not-found", true) == true -> "Akun tidak ditemukan."
                            else -> "Gagal: ${it.localizedMessage}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepRose),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text(if (isRegister) "DAFTAR" else "MASUK", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { isRegister = !isRegister; message = "" }) {
            Text(
                text = if (isRegister) "Sudah punya akun? Masuk di sini" else "Belum punya akun? Daftar gratis",
                color = Espresso
            )
        }

        TextButton(onClick = onBack) {
            Text("Kembali", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
