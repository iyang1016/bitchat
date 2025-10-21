package com.bitchat.android.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Manages LE Coded PHY functionality for extended range mesh networking
 * 
 * LE Coded PHY provides:
 * - Up to 8x range extension (up to 1km line-of-sight in optimal conditions)
 * - Better signal penetration through obstacles
 * - Forward Error Correction for improved reliability
 * - Two coding schemes: S=2 (2x range, 500kbps) and S=8 (8x range, 125kbps)
 * 
 * Requirements:
 * - Android 8.0+ (API 26)
 * - Bluetooth 5.0+ hardware
 * - Device support for LE Coded PHY
 */
class CodedPhyManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CodedPhyManager"
        
        // PHY constants from BluetoothDevice (API 26+)
        private const val PHY_LE_1M = 1
        private const val PHY_LE_2M = 2
        private const val PHY_LE_CODED = 3
        
        // Coded PHY options
        private const val PHY_OPTION_NO_PREFERRED = 0
        private const val PHY_OPTION_S2 = 1  // 2x range, 500 kbps
        private const val PHY_OPTION_S8 = 2  // 4x range, 125 kbps
    }
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    // Feature detection
    private var isCodedPhySupported: Boolean = false
    private var isCodedPhyEnabled: Boolean = false
    private var powerAwareMode: Boolean = true // Enable power-aware Coded PHY usage
    
    init {
        detectCodedPhySupport()
    }
    
    /**
     * Detect if device supports LE Coded PHY
     */
    private fun detectCodedPhySupport() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.i(TAG, "LE Coded PHY requires Android 8.0+")
            return
        }
        
        bluetoothAdapter?.let { adapter ->
            try {
                // Check if adapter supports LE Coded PHY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    isCodedPhySupported = adapter.isLeCodedPhySupported
                    Log.i(TAG, "LE Coded PHY supported: $isCodedPhySupported")
                    
                    if (isCodedPhySupported) {
                        // Enable by default if supported
                        isCodedPhyEnabled = true
                        Log.i(TAG, "LE Coded PHY enabled for extended range")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error detecting LE Coded PHY support: ${e.message}")
                isCodedPhySupported = false
            }
        }
    }
    
    /**
     * Check if LE Coded PHY is supported and enabled
     */
    fun isCodedPhyAvailable(): Boolean = isCodedPhySupported && isCodedPhyEnabled
    
    /**
     * Check if LE Coded PHY should be used based on power mode
     */
    fun shouldUseCodedPhy(powerMode: PowerManager.PowerMode): Boolean {
        if (!isCodedPhyAvailable()) return false
        
        return if (powerAwareMode) {
            when (powerMode) {
                PowerManager.PowerMode.PERFORMANCE -> true
                PowerManager.PowerMode.BALANCED -> true
                PowerManager.PowerMode.POWER_SAVER -> false // Disable in power save mode
                PowerManager.PowerMode.ULTRA_LOW_POWER -> false // Disable in ultra low power
            }
        } else {
            true // Always use if manually enabled
        }
    }
    
    /**
     * Enable/disable LE Coded PHY usage
     */
    fun setCodedPhyEnabled(enabled: Boolean) {
        if (!isCodedPhySupported) {
            Log.w(TAG, "Cannot enable LE Coded PHY - not supported by device")
            return
        }
        
        isCodedPhyEnabled = enabled
        Log.i(TAG, "LE Coded PHY ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Enable/disable power-aware LE Coded PHY usage
     */
    fun setPowerAwareMode(enabled: Boolean) {
        powerAwareMode = enabled
        Log.i(TAG, "LE Coded PHY power-aware mode ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Get power-aware mode status
     */
    fun isPowerAwareMode(): Boolean = powerAwareMode
    
    /**
     * Get scan settings with LE Coded PHY support
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getCodedPhyScanSettings(baseScanSettings: ScanSettings): ScanSettings {
        if (!isCodedPhyAvailable()) {
            return baseScanSettings
        }
        
        return try {
            val builder = ScanSettings.Builder()
                .setScanMode(baseScanSettings.scanMode)
                .setCallbackType(baseScanSettings.callbackType)
                .setReportDelay(baseScanSettings.reportDelayMillis)
            
            // Try to set PHY via reflection for API compatibility
            try {
                val setPhyMethod = ScanSettings.Builder::class.java.getMethod("setPhy", Int::class.javaPrimitiveType)
                setPhyMethod.invoke(builder, 3) // PHY_LE_ALL_SUPPORTED = 3
            } catch (_: NoSuchMethodException) {
                // Method not available on this SDK - ignore
            } catch (e: Exception) {
                Log.w(TAG, "setPhy reflection failed: ${e.message}")
            }
            
            builder.build()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Coded PHY scan settings: ${e.message}")
            baseScanSettings
        }
    }
    
    /**
     * Get advertising settings with LE Coded PHY support
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getCodedPhyAdvertiseSettings(baseAdvertiseSettings: AdvertiseSettings): AdvertiseSettings {
        if (!isCodedPhyAvailable()) {
            return baseAdvertiseSettings
        }
        
        return try {
            val builder = AdvertiseSettings.Builder()
                .setAdvertiseMode(baseAdvertiseSettings.mode)
                .setTxPowerLevel(baseAdvertiseSettings.txPowerLevel)
                .setConnectable(baseAdvertiseSettings.isConnectable)
                .setTimeout(baseAdvertiseSettings.timeout)
            
            // Try to set PHY via reflection for API compatibility
            try {
                val primaryPhyMethod = AdvertiseSettings.Builder::class.java.getMethod("setPrimaryPhy", Int::class.javaPrimitiveType)
                val secondaryPhyMethod = AdvertiseSettings.Builder::class.java.getMethod("setSecondaryPhy", Int::class.javaPrimitiveType)
                primaryPhyMethod.invoke(builder, 3) // PHY_LE_CODED = 3
                secondaryPhyMethod.invoke(builder, 3) // PHY_LE_CODED = 3
            } catch (_: NoSuchMethodException) {
                // Methods not available on this SDK - will use GATT PHY requests instead
            } catch (e: Exception) {
                Log.w(TAG, "Advertise PHY reflection failed: ${e.message}")
            }
            
            builder.build()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Coded PHY advertise settings: ${e.message}")
            baseAdvertiseSettings
        }
    }
    
    /**
     * Request PHY change for an established GATT connection
     * This optimizes the connection for extended range
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun requestCodedPhy(gatt: BluetoothGatt, preferS8: Boolean = false) {
        if (!isCodedPhyAvailable()) {
            return
        }
        
        try {
            val phyOption = if (preferS8) PHY_OPTION_S8 else PHY_OPTION_S2
            
            // Request LE Coded PHY for both TX and RX
            gatt.setPreferredPhy(
                PHY_LE_CODED,  // txPhy
                PHY_LE_CODED,  // rxPhy  
                phyOption      // phyOptions (S2 or S8)
            )
            
            Log.d(TAG, "Requested LE Coded PHY (${if (preferS8) "S=8" else "S=2"}) for ${gatt.device.address}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request Coded PHY: ${e.message}")
        }
    }
    
    /**
     * Enhanced GATT callback that handles PHY updates
     */
    abstract class CodedPhyGattCallback : BluetoothGattCallback() {
        
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status)
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val txPhyName = getPhyName(txPhy)
                val rxPhyName = getPhyName(rxPhy)
                Log.i(TAG, "PHY updated for ${gatt?.device?.address}: TX=$txPhyName, RX=$rxPhyName")
                
                // Notify about successful Coded PHY activation
                if (txPhy == PHY_LE_CODED || rxPhy == PHY_LE_CODED) {
                    onCodedPhyActivated(gatt, txPhy, rxPhy)
                }
            } else {
                Log.w(TAG, "PHY update failed for ${gatt?.device?.address}: status=$status")
                onCodedPhyFailed(gatt, status)
            }
        }
        
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(gatt, txPhy, rxPhy, status)
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val txPhyName = getPhyName(txPhy)
                val rxPhyName = getPhyName(rxPhy)
                Log.d(TAG, "Current PHY for ${gatt?.device?.address}: TX=$txPhyName, RX=$rxPhyName")
                
                onPhyStatus(gatt, txPhy, rxPhy)
            }
        }
        
        private fun getPhyName(phy: Int): String = when (phy) {
            PHY_LE_1M -> "LE 1M"
            PHY_LE_2M -> "LE 2M" 
            PHY_LE_CODED -> "LE Coded"
            else -> "Unknown ($phy)"
        }
        
        /**
         * Called when LE Coded PHY is successfully activated
         */
        abstract fun onCodedPhyActivated(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int)
        
        /**
         * Called when LE Coded PHY activation fails
         */
        abstract fun onCodedPhyFailed(gatt: BluetoothGatt?, status: Int)
        
        /**
         * Called with current PHY status
         */
        abstract fun onPhyStatus(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int)
    }
    
    /**
     * Get status information for debugging
     */
    fun getStatusInfo(): String {
        return buildString {
            appendLine("LE Coded PHY Status:")
            appendLine("- Android Version: ${Build.VERSION.SDK_INT} (required: 26+)")
            appendLine("- Hardware Support: $isCodedPhySupported")
            appendLine("- Feature Enabled: $isCodedPhyEnabled")
            appendLine("- Available: ${isCodedPhyAvailable()}")
            
            if (isCodedPhyAvailable()) {
                appendLine("- Range Extension: Up to 8x (1km line-of-sight in optimal conditions)")
                appendLine("- Coding Schemes: S=2 (2x range, 500kbps), S=8 (8x range, 125kbps)")
                appendLine("- Benefits: Better penetration, FEC error correction")
                appendLine("- Power Aware Mode: $powerAwareMode")
            }
        }
    }
}