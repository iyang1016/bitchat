package com.bitchat.android.activation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.MainActivity
import com.bitchat.android.ui.theme.BitchatTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActivationActivity : ComponentActivity() {
    
    private lateinit var activationManager: ActivationManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        activationManager = ActivationManager(this)
        
        if (activationManager.isVerified()) {
            navigateToMain()
            return
        }
        
        setContent {
            BitchatTheme {
                ActivationScreen(
                    activationManager = activationManager,
                    onActivated = { navigateToMain() }
                )
            }
        }
    }
    
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    activationManager: ActivationManager,
    onActivated: () -> Unit
) {
    var screenState by remember { mutableStateOf(ActivationScreenState.INITIAL) }
    var showCodeInput by remember { mutableStateOf(false) }
    var activationCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val deviceInfo = remember { activationManager.getDeviceInfo() }
    
    LaunchedEffect(Unit) {
        if (activationManager.hasRequestedAccess()) {
            screenState = ActivationScreenState.PENDING
            startStatusPolling(activationManager, onActivated) { message ->
                statusMessage = message
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (screenState) {
                    ActivationScreenState.INITIAL -> Icons.Filled.Lock
                    ActivationScreenState.PENDING -> Icons.Filled.Pending
                    ActivationScreenState.APPROVED -> Icons.Filled.CheckCircle
                },
                contentDescription = "Status",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "BitChat Modded",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            AnimatedContent(targetState = screenState, label = "status") { state ->
                Text(
                    text = when (state) {
                        ActivationScreenState.INITIAL -> "Access Required"
                        ActivationScreenState.PENDING -> "Waiting for Approval"
                        ActivationScreenState.APPROVED -> "Approved!"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device Information", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(deviceInfo.model, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Android ${deviceInfo.androidVersion}", style = MaterialTheme.typography.bodySmall)
                    Text("ID: ${deviceInfo.deviceId.take(16)}...", 
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (statusMessage.isNotEmpty()) {
                Text(statusMessage, style = MaterialTheme.typography.bodyMedium, 
                    color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            when (screenState) {
                ActivationScreenState.INITIAL -> {
                    Button(
                        onClick = {
                            scope.launch {
                                screenState = ActivationScreenState.PENDING
                                val result = activationManager.requestAccess()
                                result.onSuccess { response ->
                                    if (response.approved) {
                                        screenState = ActivationScreenState.APPROVED
                                        delay(1000)
                                        onActivated()
                                    } else {
                                        startStatusPolling(activationManager, onActivated) { statusMessage = it }
                                    }
                                }.onFailure {
                                    errorMessage = "Failed to send request"
                                    screenState = ActivationScreenState.INITIAL
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Request Access", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                ActivationScreenState.PENDING -> {
                    CircularProgressIndicator()
                }
                else -> {}
            }
            
            if (screenState != ActivationScreenState.APPROVED) {
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = { showCodeInput = !showCodeInput }) {
                    Text(if (showCodeInput) "Hide Code Entry" else "Have an activation code?")
                }
                
                AnimatedVisibility(visible = showCodeInput) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = activationCode,
                            onValueChange = { activationCode = it.uppercase(); errorMessage = null },
                            label = { Text("Activation Code") },
                            placeholder = { Text("XXXX-XXXX-XXXX") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = errorMessage != null,
                            supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    scope.launch {
                                        val result = activationManager.verifyWithCode(activationCode)
                                        result.onSuccess { success ->
                                            if (success) {
                                                screenState = ActivationScreenState.APPROVED
                                                delay(1000)
                                                onActivated()
                                            } else {
                                                errorMessage = "Invalid code"
                                            }
                                        }.onFailure { errorMessage = "Verification failed" }
                                    }
                                }
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val result = activationManager.verifyWithCode(activationCode)
                                    result.onSuccess { success ->
                                        if (success) {
                                            screenState = ActivationScreenState.APPROVED
                                            delay(1000)
                                            onActivated()
                                        } else {
                                            errorMessage = "Invalid code"
                                        }
                                    }.onFailure { errorMessage = "Verification failed" }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = activationCode.isNotBlank()
                        ) {
                            Text("Verify Code")
                        }
                    }
                }
            }
            
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(errorMessage!!, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun CoroutineScope.startStatusPolling(
    activationManager: ActivationManager,
    onActivated: () -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    launch {
        var attempts = 0
        while (attempts < 120) {
            delay(30000)
            attempts++
            val result = activationManager.checkApprovalStatus()
            result.onSuccess { status ->
                if (status.approved) {
                    onStatusUpdate("Approved! Launching...")
                    delay(1000)
                    onActivated()
                    return@launch
                } else {
                    onStatusUpdate("Checking... (${attempts * 30}s)")
                }
            }
        }
        onStatusUpdate("Timeout. Restart app.")
    }
}

enum class ActivationScreenState {
    INITIAL,
    PENDING,
    APPROVED
}
