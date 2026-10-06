package com.example.agv_rfid_manager.device

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 비정상 종료(미처리 예외) 시 스택 트레이스를 앱 내부 저장소에 기록
object CrashLogger {
    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_CHARS = 100_000
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try { write(appContext, "CRASH [${thread.name}]", e) } catch (_: Throwable) {}
            // 기존 핸들러로 넘겨서 정상적인 종료 처리
            defaultHandler?.uncaughtException(thread, e)
        }
    }

    @Synchronized
    fun write(context: Context, title: String, e: Throwable) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val ver = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { "?" }
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val entry = "===== $time $title =====\nv$ver / ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}\n$sw\n"
            val old = if (file.exists()) file.readText() else ""
            // 최신 기록이 위로, 최대 크기 유지
            file.writeText((entry + old).take(MAX_CHARS))
        } catch (_: Throwable) {}
    }

    fun read(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        return try { if (file.exists()) file.readText() else "" } catch (_: Exception) { "" }
    }

    fun clear(context: Context) {
        try { File(context.filesDir, FILE_NAME).delete() } catch (_: Exception) {}
    }
}
