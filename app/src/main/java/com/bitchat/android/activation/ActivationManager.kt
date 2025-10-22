package com.bitchat.android.activation

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.delay
import java.util.UUID

class ActivationManager(private val context: Context) {
    
    // Obfuscated preference file name
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            "app_sys_config",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to regular SharedPreferences if encryption fails
        context.getSharedPreferences("app_sys_config_fallback", Context.MODE_PRIVATE)
    }
    
    private val api = ActivationApi()
    
    companion object {
        // Obfuscated storage keys
        private const val KEY_VERIFIED = "sys_state_v2"
        private const val KEY_DEVICE_ID = "hw_id_hash"
        private const val KEY_VERIFICATION_TIME = "ts_init"
        private const val KEY_REQUEST_SENT = "net_req_flag"
        
        // Anti-tampering checksum
        private const val INTEGRITY_CHECK = "d4f8a9b2c1e3"
    }
    
    fun isVerified(): Boolean {
        // Root/tamper detection
        if (isDeviceCompromised()) {
            return false
        }
        
        // Anti-tampering: verify integrity
        val verified = prefs.getBoolean(KEY_VERIFIED, false)
        val timestamp = prefs.getLong(KEY_VERIFICATION_TIME, 0)
        
        // Check if activation is legitimate (has timestamp)
        if (verified && timestamp == 0L) {
            // Tampered - reset
            prefs.edit().clear().apply()
            return false
        }
        
        return verified
    }
    
    private fun isDeviceCompromised(): Boolean {
        return try {
            // Check for root/debugging
            val buildTags = android.os.Build.TAGS
            if (buildTags != null && buildTags.contains("test-keys")) {
                return true
            }
            
            // Check for common root files
            val paths = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            
            for (path in paths) {
                if (java.io.File(path).exists()) {
                    return true
                }
            }
            
            false
        } catch (e: Exception) {
            // If root detection fails, allow access
            false
        }
    }
    
    fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = generateDeviceId()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }
    
    private fun generateDeviceId(): String {
        // Try to get Android ID first
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        
        // Fallback to UUID if Android ID is not available
        return if (androidId != null && androidId != "9774d56d682e549c") {
            "android_$androidId"
        } else {
            "uuid_${UUID.randomUUID()}"
        }
    }
    
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            deviceId = getDeviceId(),
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT
        )
    }
    
    suspend fun requestAccess(): Result<RequestResponse> {
        return try {
            val deviceInfo = getDeviceInfo()
            val response = api.requestAccess(deviceInfo)
            
            if (response.success) {
                prefs.edit().putBoolean(KEY_REQUEST_SENT, true).apply()
                
                // If instantly approved (e.g., via code)
                if (response.approved) {
                    markAsVerified()
                }
            }
            
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun checkApprovalStatus(): Result<ApprovalStatus> {
        return try {
            val deviceId = getDeviceId()
            val status = api.checkStatus(deviceId)
            
            if (status.approved) {
                markAsVerified()
            }
            
            Result.success(status)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun verifyWithCode(code: String): Result<Boolean> {
        return try {
            val deviceInfo = getDeviceInfo()
            val response = api.verifyWithCode(deviceInfo, code)
            
            if (response.success && response.approved) {
                markAsVerified()
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun markAsVerified() {
        prefs.edit().apply {
            putBoolean(KEY_VERIFIED, true)
            putLong(KEY_VERIFICATION_TIME, System.currentTimeMillis())
            apply()
        }
    }
    
    fun hasRequestedAccess(): Boolean {
        return prefs.getBoolean(KEY_REQUEST_SENT, false)
    }
    
    fun reset() {
        prefs.edit().clear().apply()
    }
}

data class DeviceInfo(
    val deviceId: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int
)

data class RequestResponse(
    val success: Boolean,
    val approved: Boolean = false,
    val message: String = ""
)

data class ApprovalStatus(
    val approved: Boolean,
    val pending: Boolean,
    val rejected: Boolean = false,
    val message: String = ""
)
