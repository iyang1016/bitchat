package com.bitchat.android.activation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PauseCircle
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
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val deviceInfo = remember { activationManager.getDeviceInfo() }
    
    LaunchedEffect(Unit) {
        if (activationManager.hasRequestedAccess()) {
            // Check current status first
            val result = activationManager.checkApprovalStatus()
            result.onSuccess { status ->
                when {
                    status.paused -> {
                        screenState = ActivationScreenState.PAUSED
                        statusMessage = "Your access has been paused"
                    }
                    status.approved -> {
                        screenState = ActivationScreenState.APPROVED
                        delay(500)
                        onActivated()
                    }
                    status.rejected -> {
                        screenState = ActivationScreenState.INITIAL
                        errorMessage = "Access was denied. Please request again."
                    }
                    else -> {
                        screenState = ActivationScreenState.PENDING
                        startStatusPolling(activationManager, onActivated) { message ->
                            statusMessage = message
                        }
                    }
                }
            }.onFailure {
                // If check fails, assume pending
                screenState = ActivationScreenState.PENDING
                startStatusPolling(activationManager, onActivated) { message ->
                    statusMessage = message
                }
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
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Icon(
                imageVector = when (screenState) {
                    ActivationScreenState.INITIAL -> Icons.Filled.Lock
                    ActivationScreenState.PENDING -> Icons.Filled.Pending
                    ActivationScreenState.APPROVED -> Icons.Filled.CheckCircle
                    ActivationScreenState.PAUSED -> Icons.Filled.PauseCircle
                },
                contentDescription = "Status",
                modifier = Modifier.size(80.dp),
                tint = when (screenState) {
                    ActivationScreenState.PAUSED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
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
                        ActivationScreenState.PAUSED -> "Access Paused"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when (state) {
                        ActivationScreenState.PAUSED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    }
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
            
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
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
                                isLoading = true
                                errorMessage = null
                                val result = activationManager.requestAccess()
                                result.onSuccess { response ->
                                    if (response.approved) {
                                        screenState = ActivationScreenState.APPROVED
                                        delay(1000)
                                        onActivated()
                                    } else {
                                        isLoading = false
                                        screenState = ActivationScreenState.PENDING
                                        statusMessage = response.message
                                        startStatusPolling(activationManager, onActivated) { statusMessage = it }
                                    }
                                }.onFailure {
                                    isLoading = false
                                    errorMessage = "Failed to send request: ${it.message}"
                                    screenState = ActivationScreenState.INITIAL
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Request Access", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                ActivationScreenState.PENDING -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Waiting for admin approval...",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Check the admin dashboard to approve this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                ActivationScreenState.PAUSED -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "⏸️ Your Access is Paused",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Your device access has been temporarily paused by the administrator.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Please contact the developer to resume your access.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    statusMessage = "Checking access status..."
                                    
                                    val result = activationManager.checkApprovalStatus()
                                    result.onSuccess { status ->
                                        if (status.paused) {
                                            isLoading = false
                                            errorMessage = "⏸️ Access still paused. Please contact the developer."
                                            statusMessage = ""
                                        } else if (status.approved) {
                                            statusMessage = "✅ Access restored! Launching..."
                                            screenState = ActivationScreenState.APPROVED
                                            delay(800)
                                            onActivated()
                                        } else {
                                            isLoading = false
                                            screenState = ActivationScreenState.INITIAL
                                            statusMessage = "Access was revoked. Please request access again."
                                        }
                                    }.onFailure {
                                        isLoading = false
                                        errorMessage = "Failed to check status: ${it.message}"
                                        statusMessage = ""
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("🔄 Retry Access", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Tap the button above to check if your access has been restored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {}
            }
            
            if (screenState != ActivationScreenState.APPROVED && screenState != ActivationScreenState.PAUSED) {
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
                                    if (activationCode.isNotBlank() && !isLoading) {
                                        scope.launch {
                                            isLoading = true
                                            errorMessage = null
                                            statusMessage = "Verifying code..."
                                            
                                            val result = activationManager.verifyWithCode(activationCode)
                                            result.onSuccess { success ->
                                                if (success) {
                                                    statusMessage = "✅ Code verified! Launching..."
                                                    screenState = ActivationScreenState.APPROVED
                                                    delay(800)
                                                    onActivated()
                                                } else {
                                                    isLoading = false
                                                    errorMessage = "❌ Invalid or expired code"
                                                    statusMessage = ""
                                                }
                                            }.onFailure { 
                                                isLoading = false
                                                errorMessage = "❌ Verification failed: ${it.message}"
                                                statusMessage = ""
                                            }
                                        }
                                    }
                                }
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    statusMessage = "Verifying code..."
                                    
                                    val result = activationManager.verifyWithCode(activationCode)
                                    result.onSuccess { success ->
                                        if (success) {
                                            statusMessage = "✅ Code verified! Launching..."
                                            screenState = ActivationScreenState.APPROVED
                                            delay(800)
                                            onActivated()
                                        } else {
                                            isLoading = false
                                            errorMessage = "❌ Invalid or expired code"
                                            statusMessage = ""
                                        }
                                    }.onFailure { 
                                        isLoading = false
                                        errorMessage = "❌ Verification failed: ${it.message}"
                                        statusMessage = ""
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = activationCode.isNotBlank() && !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Verify Code", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
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
        var shouldStop = false
        var wasApproved = false
        
        // ULTRA-FAST polling: check IMMEDIATELY, then every 1s for first 30s, then 3s, then 10s
        while (attempts < 200 && !shouldStop) {
            // Check immediately on first attempt, then delay
            if (attempts > 0) {
                val delayTime = when {
                    attempts < 30 -> 1000L   // First 30 seconds: check every 1 second (FAST!)
                    attempts < 60 -> 3000L   // Next 90 seconds: check every 3 seconds
                    else -> 10000L           // After 2 minutes: check every 10 seconds
                }
                delay(delayTime)
            }
            attempts++
            
            try {
                kotlinx.coroutines.withTimeout(5000) { // 5 second timeout per check
                    val result = activationManager.checkApprovalStatus()
                    result.onSuccess { status ->
                        if (status.paused) {
                            shouldStop = true
                            wasApproved = false
                            onStatusUpdate("⏸️ Device paused by admin")
                        } else if (status.approved) {
                            shouldStop = true
                            wasApproved = true
                            onStatusUpdate("✅ Approved! Launching...")
                        } else if (status.rejected) {
                            shouldStop = true
                            wasApproved = false
                            onStatusUpdate("❌ Access denied")
                        } else {
                            val elapsed = when {
                                attempts <= 30 -> attempts
                                attempts <= 60 -> 30 + (attempts - 30) * 3
                                else -> 120 + (attempts - 60) * 10
                            }
                            onStatusUpdate("⏳ Checking... (${elapsed}s)")
                        }
                    }.onFailure {
                        // Network error - show but continue
                        onStatusUpdate("⚠️ Connection issue, retrying...")
                    }
                }
                
                // Only activate if approved, not if paused or rejected
                if (shouldStop) {
                    delay(500)
                    if (wasApproved) {
                        onActivated()
                    }
                    return@launch
                }
            } catch (e: Exception) {
                // Timeout or error - continue polling silently
                onStatusUpdate("⚠️ Retrying...")
            }
        }
        onStatusUpdate("⏱️ Timeout. Please restart app or use activation code.")
    }
}

enum class ActivationScreenState {
    INITIAL,
    PENDING,
    APPROVED,
    PAUSED
}
