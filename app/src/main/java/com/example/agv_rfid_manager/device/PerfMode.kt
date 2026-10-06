package com.example.agv_rfid_manager.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build

// 저사양 기기 자동 판정
object PerfMode {
    // RAM 4GB 표기 기기의 실제 totalMem은 약 3.6GB 전후라 3.5GiB 미만을 4GB 미만 기기로 판단
    private const val LOW_RAM_BYTES = 3_500L * 1024 * 1024

    fun detectLowEnd(context: Context): Boolean {
        // 실시간 blur(RenderEffect)는 Android 12(API 31)부터 지원
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (am.isLowRamDevice) return true
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem in 1 until LOW_RAM_BYTES
        } catch (_: Exception) {
            false
        }
    }
}
