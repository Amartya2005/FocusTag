package com.focustag.app.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.focustag.app.ui.components.FunkyStage

@Composable
fun SignupScreen(viewModel: AuthViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.authSuccess.collect {
            Toast.makeText(context, "Check your email to finish signup", Toast.LENGTH_LONG).show()
        }
    }

    FunkyStage {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(28.dp)) {
                    Text("FOCUSTAG", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Join class", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Same email the school enrolled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedTextField(value = state.name, onValueChange = viewModel::onNameChanged, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(value = state.email, onValueChange = viewModel::onEmailChanged, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, shape = RoundedCornerShape(16.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(value = state.password, onValueChange = viewModel::onPasswordChanged, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, shape = RoundedCornerShape(16.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(value = state.confirmPassword, onValueChange = viewModel::onConfirmPasswordChanged, label = { Text("Confirm") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, shape = RoundedCornerShape(16.dp))
                    state.errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = viewModel::onSignupClicked, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !state.isLoading, shape = RoundedCornerShape(16.dp)) {
                        if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text("Create account")
                    }
                    TextButton(onClick = viewModel::toggleAuthMode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Have an account? Sign in")
                    }
                }
            }
        }
    }
}
