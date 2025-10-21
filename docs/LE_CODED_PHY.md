# LE Coded PHY Implementation in BitChat

This document describes the implementation of LE Coded PHY (Bluetooth 5.0+ Long Range) support in BitChat Android for extended mesh networking range.

## Overview

LE Coded PHY is a Bluetooth 5.0+ feature that provides significant range extension for Low Energy connections at the cost of reduced data rates. This is particularly valuable for BitChat's mesh networking architecture.

### Benefits for BitChat

- **4x Range Extension**: Up to 240m line-of-sight vs 60m for standard 1M PHY
- **Better Penetration**: Improved signal through walls and obstacles  
- **Mesh Efficiency**: Larger hop distances reduce relay requirements
- **Reliability**: Forward Error Correction (FEC) reduces packet loss
- **Emergency Use**: Better performance in challenging RF environments

### Technical Details

**Coding Schemes:**
- **S=2**: 2x range extension, 500 kbps data rate
- **S=8**: 4x range extension, 125 kbps data rate

**Requirements:**
- Android 8.0+ (API 26)
- Bluetooth 5.0+ hardware
- Device support for LE Coded PHY

## Implementation Architecture

### Core Components

#### 1. CodedPhyManager
**File:** `app/src/main/java/com/bitchat/android/mesh/CodedPhyManager.kt`

Main manager class that handles:
- Feature detection and capability checking
- Scan/advertise settings modification for Coded PHY
- GATT connection PHY negotiation
- Enhanced GATT callback with PHY update handling

```kotlin
class CodedPhyManager(private val context: Context) {
    fun isCodedPhyAvailable(): Boolean
    fun setCodedPhyEnabled(enabled: Boolean)
    fun getCodedPhyScanSettings(baseScanSettings: ScanSettings): ScanSettings
    fun getCodedPhyAdvertiseSettings(baseAdvertiseSettings: AdvertiseSettings): AdvertiseSettings
    fun requestCodedPhy(gatt: BluetoothGatt, preferS8: Boolean = false)
}
```

#### 2. PowerManager Integration
**File:** `app/src/main/java/com/bitchat/android/mesh/PowerManager.kt`

PowerManager now includes CodedPhyManager and automatically applies Coded PHY settings:

```kotlin
// Apply LE Coded PHY if supported and Android 8.0+
return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && codedPhyManager.isCodedPhyAvailable()) {
    codedPhyManager.getCodedPhyScanSettings(baseScanSettings)
} else {
    baseScanSettings
}
```

#### 3. GATT Client/Server Integration
**Files:** 
- `app/src/main/java/com/bitchat/android/mesh/BluetoothGattClientManager.kt`
- `app/src/main/java/com/bitchat/android/mesh/BluetoothGattServerManager.kt`

Both GATT managers now:
- Use enhanced CodedPhyGattCallback for PHY update handling
- Automatically request Coded PHY after connection establishment
- Log PHY activation success/failure for debugging

### Enhanced GATT Callback

The `CodedPhyManager.CodedPhyGattCallback` extends `BluetoothGattCallback` with PHY-specific methods:

```kotlin
abstract class CodedPhyGattCallback : BluetoothGattCallback() {
    abstract fun onCodedPhyActivated(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int)
    abstract fun onCodedPhyFailed(gatt: BluetoothGatt?, status: Int)
    abstract fun onPhyStatus(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int)
}
```

## Configuration and Control

### Debug Settings Integration

LE Coded PHY can be controlled through the debug settings sheet:

- **Toggle**: Enable/disable LE Coded PHY usage
- **Status Display**: Shows hardware support and current state
- **Range Information**: Displays expected range benefits

### Default Behavior

- **Auto-Enable**: Automatically enabled if hardware supports it
- **S=2 Preference**: Uses S=2 coding by default for balance of range/speed
- **Graceful Fallback**: Falls back to standard 1M PHY if Coded PHY fails

## Usage Patterns

### Automatic Operation

LE Coded PHY operates transparently:

1. **Detection**: Automatically detects hardware support on startup
2. **Scanning**: Applies Coded PHY to scan settings if available
3. **Advertising**: Uses Coded PHY for advertising if supported
4. **Connections**: Requests Coded PHY upgrade after GATT connection
5. **Fallback**: Gracefully handles unsupported devices

### Connection Flow

```
1. Standard BLE connection established
2. MTU negotiation
3. LE Coded PHY request (if supported)
4. PHY update callback
5. Service discovery continues
6. Data transfer with extended range
```

## Performance Characteristics

### Range Extension

| PHY Type | Typical Range | Line-of-Sight | Data Rate |
|----------|---------------|---------------|-----------|
| LE 1M    | 10-30m        | 60m          | 1 Mbps    |
| LE Coded S=2 | 20-60m    | 120m         | 500 kbps  |
| LE Coded S=8 | 40-120m   | 240m         | 125 kbps  |

### BitChat Suitability

LE Coded PHY is well-suited for BitChat because:
- **Low Data Requirements**: Text messages and small files fit within reduced data rates
- **Mesh Benefits**: Extended range reduces hop count in mesh networks
- **Battery Efficiency**: Fewer hops mean less relay traffic and better battery life
- **Reliability**: FEC improves message delivery in noisy environments

## Compatibility

### Cross-Platform

- **iOS Compatibility**: BitChat iOS should implement similar LE Coded PHY support
- **Protocol Compatibility**: Binary protocol remains unchanged
- **Graceful Degradation**: Mixed networks with/without Coded PHY support work correctly

### Device Support

- **Android Requirements**: API 26+ (Android 8.0)
- **Hardware Requirements**: Bluetooth 5.0+ chipset with LE Coded PHY support
- **Fallback**: Devices without support continue using standard 1M PHY

## Testing and Validation

### Unit Tests
**File:** `app/src/test/kotlin/com/bitchat/CodedPhyTest.kt`

Tests cover:
- Manager initialization
- Feature detection logic
- Status information accuracy
- Enable/disable functionality

### Field Testing

Recommended testing scenarios:
1. **Range Testing**: Measure actual range improvement in various environments
2. **Mixed Networks**: Test networks with both Coded PHY and standard devices
3. **Battery Impact**: Monitor power consumption with Coded PHY enabled
4. **Reliability**: Test message delivery in challenging RF environments

## Debugging and Monitoring

### Log Messages

Key log messages to monitor:
```
✅ LE Coded PHY activated for [address] - Extended range enabled
⚠️ LE Coded PHY activation failed for [address]: status=[code]
PHY updated for [address]: TX=LE Coded, RX=LE Coded
```

### Debug Information

The debug settings sheet shows:
- Hardware support status
- Current enable/disable state
- Expected range benefits
- Android version compatibility

### Status Information

`CodedPhyManager.getStatusInfo()` provides comprehensive status:
```
LE Coded PHY Status:
- Android Version: 30 (required: 26+)
- Hardware Support: true
- Feature Enabled: true
- Available: true
- Range Extension: Up to 4x (240m line-of-sight)
- Coding Schemes: S=2 (500kbps), S=8 (125kbps)
- Benefits: Better penetration, FEC error correction
```

## Future Enhancements

### Potential Improvements

1. **Adaptive PHY Selection**: Dynamically choose between S=2 and S=8 based on conditions
2. **Range-Based Routing**: Prefer Coded PHY connections for long-distance hops
3. **Power Optimization**: Disable Coded PHY in ultra-low power modes
4. **Statistics Collection**: Track range improvements and connection success rates

### iOS Integration

For full mesh benefits, the iOS BitChat client should implement equivalent LE Coded PHY support using Core Bluetooth's PHY management APIs.

## Conclusion

LE Coded PHY implementation significantly enhances BitChat's mesh networking capabilities by extending range and improving reliability. The implementation is designed to be transparent to users while providing substantial benefits for mesh network topology and emergency communication scenarios.

The feature automatically activates on supported hardware and gracefully falls back on older devices, ensuring broad compatibility while maximizing performance on modern Bluetooth 5.0+ devices.