package com.rts.rys.ryy.drawingtogether.ui.pairing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

// Nearby Connections에 필요한 런타임 권한 목록. doc/nearby-connections.md §3.
// minSdk 30. API 레벨별 3분기:
//  - API 33+  : BT 신권한 3종 + NEARBY_WIFI_DEVICES (neverForLocation, 위치 불필요)
//  - API 31-32: BT 신권한 3종 (neverForLocation, 위치 불필요)
//  - API 30   : ACCESS_FINE_LOCATION (BT 스캔에 위치 필요). BLUETOOTH/BLUETOOTH_ADMIN 은
//               일반(install-time) 권한이라 런타임 요청 대상이 아님 → 목록에서 제외.
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
