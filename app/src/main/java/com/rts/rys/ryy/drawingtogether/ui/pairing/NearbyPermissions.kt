package com.rts.rys.ryy.drawingtogether.ui.pairing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

// Nearby Connections에 필요한 권한 목록. doc/nearby-connections.md §3.
// API 33+: NEARBY_WIFI_DEVICES + BT 신권한
// API 31-32: BT 신권한
// API ≤30: 레거시 BT + ACCESS_FINE_LOCATION
object NearbyPermissions {
    fun required(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )
        else -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    fun allGranted(context: Context): Boolean = required().all { p ->
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }
}
