# Device Activation System

## Overview

BitChat Modded includes a device activation system that requires approval before users can access the app.

## How It Works

### User Flow:
1. User installs and opens app
2. App shows activation screen with device info
3. User clicks "Request Access"
4. App polls server every 30 seconds for approval
5. Once approved by admin → user gains permanent access
6. **Optional**: User can enter activation code for instant approval

### Features:
- ✅ One-time activation (works offline after approval)
- ✅ Approval-based (you control who gets access)
- ✅ Optional activation codes for instant access
- ✅ Encrypted local storage
- ✅ Device fingerprinting

## Configuration

### Update API Endpoint

Edit `app/src/main/java/com/bitchat/android/activation/ActivationApi.kt`:

```kotlin
private const val BASE_URL = "https://your-api-endpoint.com"
```

Replace with your actual API endpoint.

## API Requirements

Your backend API must implement these endpoints:

### POST /api/request
Request device approval
```json
Request:
{
  "device_id": "android_xxxxx",
  "model": "Samsung Galaxy S21",
  "android_version": "13",
  "sdk_version": 33
}

Response:
{
  "success": true,
  "approved": false,
  "message": "Request submitted"
}
```

### GET /api/status?device_id=xxx
Check approval status
```json
Response:
{
  "approved": true,
  "pending": false,
  "rejected": false,
  "message": "Approved"
}
```

### POST /api/verify
Verify with activation code (optional)
```json
Request:
{
  "device_id": "android_xxxxx",
  "model": "Samsung Galaxy S21",
  "android_version": "13",
  "sdk_version": 33,
  "code": "ABCD-1234-EFGH"
}

Response:
{
  "success": true,
  "approved": true,
  "message": "Activated"
}
```

## Files

- `ActivationManager.kt` - Core activation logic
- `ActivationApi.kt` - API communication
- `ActivationActivity.kt` - UI screen
- `AndroidManifest.xml` - Launcher configuration

## Testing

To test locally, you can temporarily bypass activation:

```kotlin
// In ActivationManager.kt, modify isVerified():
fun isVerified(): Boolean {
    return true // Bypass for testing
}
```

**Remember to remove this before production!**

## Security

- Device IDs are stored encrypted using Android EncryptedSharedPreferences
- API calls use HTTPS only
- Activation codes are single-use
- No sensitive data stored in plain text

## Disabling Activation

To disable activation entirely:

1. Edit `AndroidManifest.xml`
2. Change launcher activity back to `MainActivity`
3. Remove `ActivationActivity` from intent-filter

## Backend Setup

For backend implementation, you'll need:
- Database to store device approvals
- API endpoints (see above)
- Admin interface to approve/reject devices

Recommended stack:
- **Cloudflare Workers** + **Supabase** (free tier available)
- Or any backend of your choice (Node.js, Python, etc.)

Contact the developer for backend setup assistance.
