package com.bitchat.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.mesh.PowerManager
import kotlinx.coroutines.delay

/**
 * LE Coded PHY control button with power-aware features
 * Shows current status and allows toggling the feature
 */
@Composable
fun CodedPhyButton(
    meshService: BluetoothMeshService,
    modifier: Modifier = Modifier
) {
    val codedPhyManager = meshService.connectionManager.powerManager.getCodedPhyManager()
    val powerManager = meshService.connectionManager.powerManager
    
    var isExpanded by remember { mutableStateOf(false) }
    var isCodedPhyEnabled by remember { mutableStateOf(codedPhyManager.isCodedPhyAvailable()) }
    var isPowerAware by remember { mutableStateOf(codedPhyManager.isPowerAwareMode()) }
    var currentPowerMode by remember { mutableStateOf(powerManager.getCurrentMode()) }
    
    // Update states periodically
    LaunchedEffect(Unit) {
        while (true) {
            isCodedPhyEnabled = codedPhyManager.isCodedPhyAvailable()
            isPowerAware = codedPhyManager.isPowerAwareMode()
            currentPowerMode = powerManager.getCurrentMode()
            delay(1000)
        }
    }
    
    val isSupported = codedPhyManager.isCodedPhyAvailable() || 
                     codedPhyManager.getStatusInfo().contains("Hardware Support: true")
    
    val shouldUseCodedPhy = codedPhyManager.shouldUseCodedPhy(currentPowerMode)
    
    // Animation for the rotating signal icon
    val infiniteTransition = rememberInfiniteTransition(label = "signal_animation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(modifier = modifier) {
        if (isExpanded) {
            // Expanded control panel
            Card(
                modifier = Modifier
                    .width(280.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.SettingsEthernet,
                                contentDescription = null,
                                tint = if (shouldUseCodedPhy) Color(0xFF00C851) else Color(0xFF87878700),
                                modifier = if (shouldUseCodedPhy) Modifier.rotate(rotation) else Modifier
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LE Coded PHY",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        IconButton(
                            onClick = { isExpanded = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // Status
                    val statusText = when {
                        !isSupported -> "❌ Not Supported"
                        !isCodedPhyEnabled -> "⚪ Disabled"
                        !shouldUseCodedPhy -> "🔋 Power Saving"
                        else -> "✅ Active (Up to 1km range)"
                    }
                    
                    val statusColor = when {
                        !isSupported -> Color(0xFFFF3B30)
                        !isCodedPhyEnabled -> Color(0xFF87878700)
                        !shouldUseCodedPhy -> Color(0xFFFF9500)
                        else -> Color(0xFF00C851)
                    }
                    
                    Text(
                        text = statusText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = statusColor
                    )
                    
                    if (isSupported) {
                        // Enable/Disable toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Extended Range",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Switch(
                                checked = isCodedPhyEnabled,
                                onCheckedChange = { enabled ->
                                    codedPhyManager.setCodedPhyEnabled(enabled)
                                    isCodedPhyEnabled = enabled
                                }
                            )
                        }
                        
                        // Power-aware toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Power Aware",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Switch(
                                checked = isPowerAware,
                                onCheckedChange = { enabled ->
                                    codedPhyManager.setPowerAwareMode(enabled)
                                    isPowerAware = enabled
                                }
                            )
                        }
                        
                        // Current power mode info
                        Text(
                            text = "Power Mode: $currentPowerMode",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        
                        // Range info
                        Text(
                            text = if (shouldUseCodedPhy) {
                                "🚀 Extended range active\n📡 Up to 1km line-of-sight\n🏢 Better building penetration"
                            } else {
                                "📱 Standard range (60m)\n💡 Tap to enable extended range"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = "Requires Android 8.0+ and Bluetooth 5.0+ hardware",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            // Compact floating button
            FloatingActionButton(
                onClick = { isExpanded = true },
                modifier = Modifier.size(56.dp),
                containerColor = when {
                    !isSupported -> Color(0xFFFF3B30).copy(alpha = 0.8f)
                    !isCodedPhyEnabled -> Color(0xFF87878700).copy(alpha = 0.8f)
                    !shouldUseCodedPhy -> Color(0xFFFF9500).copy(alpha = 0.8f)
                    else -> Color(0xFF00C851).copy(alpha = 0.8f)
                },
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Filled.SettingsEthernet,
                    contentDescription = "LE Coded PHY Control",
                    modifier = if (shouldUseCodedPhy) Modifier.rotate(rotation) else Modifier
                )
            }
        }
    }
}

/**
 * Compact LE Coded PHY status indicator for the header
 */
@Composable
fun CodedPhyStatusIndicator(
    meshService: BluetoothMeshService,
    modifier: Modifier = Modifier
) {
    val codedPhyManager = meshService.connectionManager.powerManager.getCodedPhyManager()
    val powerManager = meshService.connectionManager.powerManager
    
    var shouldUseCodedPhy by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        while (true) {
            shouldUseCodedPhy = codedPhyManager.shouldUseCodedPhy(powerManager.getCurrentMode())
            delay(2000)
        }
    }
    
    if (shouldUseCodedPhy) {
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF00C851))
        )
    }
}