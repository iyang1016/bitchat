package com.bitchat.android.activation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ActivationApi {
    
    companion object {
        // Obfuscated API endpoint - split to avoid easy string search
        private val BASE_URL = buildString {
            append("https://")
            append("your-worker")
            append(".workers.dev")
        }
        private const val TIMEOUT = 10000
        
        // API integrity check
        private const val API_KEY_HASH = "a7f8d9e2b4c1"
    }
    
    suspend fun requestAccess(deviceInfo: DeviceInfo): RequestResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/request")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
            }
            
            val jsonBody = JSONObject().apply {
                put("device_id", deviceInfo.deviceId)
                put("model", deviceInfo.model)
                put("android_version", deviceInfo.androidVersion)
                put("sdk_version", deviceInfo.sdkVersion)
            }
            
            connection.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray())
            }
            
            val responseCode = connection.responseCode
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            RequestResponse(
                success = responseCode == 200,
                approved = json.optBoolean("approved", false),
                message = json.optString("message", "")
            )
        } catch (e: Exception) {
            RequestResponse(
                success = false,
                message = "Network error: ${e.message}"
            )
        }
    }
    
    suspend fun checkStatus(deviceId: String): ApprovalStatus = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/status?device_id=$deviceId")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
            }
            
            val responseCode = connection.responseCode
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            ApprovalStatus(
                approved = json.optBoolean("approved", false),
                pending = json.optBoolean("pending", false),
                rejected = json.optBoolean("rejected", false),
                message = json.optString("message", "")
            )
        } catch (e: Exception) {
            ApprovalStatus(
                approved = false,
                pending = true,
                message = "Unable to check status"
            )
        }
    }
    
    suspend fun verifyWithCode(deviceInfo: DeviceInfo, code: String): RequestResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/verify")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
            }
            
            val jsonBody = JSONObject().apply {
                put("device_id", deviceInfo.deviceId)
                put("model", deviceInfo.model)
                put("android_version", deviceInfo.androidVersion)
                put("sdk_version", deviceInfo.sdkVersion)
                put("code", code)
            }
            
            connection.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray())
            }
            
            val responseCode = connection.responseCode
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            RequestResponse(
                success = responseCode == 200,
                approved = json.optBoolean("approved", false),
                message = json.optString("message", "")
            )
        } catch (e: Exception) {
            RequestResponse(
                success = false,
                message = "Verification failed: ${e.message}"
            )
        }
    }
}
