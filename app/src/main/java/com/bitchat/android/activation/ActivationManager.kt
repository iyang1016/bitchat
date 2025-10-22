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
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "bitchat_activation",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val api = ActivationApi()
    
    companion object {
        private const val KEY_VERIFIED = "is_verified"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_VERIFICATION_TIME = "verification_time"
        private const val KEY_REQUEST_SENT = "request_sent"
    }
    
    fun isVerified(): Boolean {
        return prefs.getBoolean(KEY_VERIFIED, false)
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
