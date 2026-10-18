package com.example.agv_rfid_manager.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// GitHub Release(latest-debug)에 CI가 올린 version.json
data class UpdateInfo(val code: Long, val name: String, val url: String, val notes: String)

// 대화상자 단계
sealed interface UpdStep {
    data object None : UpdStep
    data class Available(val info: UpdateInfo) : UpdStep
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdStep
    data class NeedPermission(val info: UpdateInfo, val file: File) : UpdStep
    data class Failed(val info: UpdateInfo) : UpdStep
}

// 설정 화면 '업데이트 확인' 요약 표시용
enum class CheckState { NONE, CHECKING, LATEST, AVAILABLE, FAILED }

/**
 * 앱 내 업데이트.
 * - 앱이 앞으로 올 때마다 자동 확인 (10분 간격 제한), 설정에서 수동 확인
 * - API 대신 Release 다운로드 주소 사용 → GitHub API 호출 제한(IP당 60회/시간) 영향 없음
 * - 설치는 시스템 설치 화면에서 사용자가 [업데이트]를 눌러야 함 (무음 설치 불가)
 * - 화면 회전·폴드 전환에도 다운로드가 이어지도록 Activity 밖(object)에서 상태 보관
 */
object AppUpdater {
    private const val VERSION_URL =
        "https://github.com/Leepy0/AGV_RFID_Manager_/releases/download/latest-debug/version.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private const val AUTO_INTERVAL_MS = 10 * 60 * 1000L
    private var lastAutoCheck = 0L

    var step by mutableStateOf<UpdStep>(UpdStep.None)
        private set
    var check by mutableStateOf(CheckState.NONE)
        private set
    var latest by mutableStateOf<UpdateInfo?>(null)
        private set

    fun currentCode(ctx: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    } catch (_: Exception) {
        0L
    }

    // 자동 확인 (조용히: 실패·최신이면 아무것도 안 띄움)
    // 백그라운드에 살아 있던 앱으로 돌아와도 확인하도록 onResume에서 호출, 10분 간격 제한
    fun autoCheck(ctx: Context) {
        if (step != UpdStep.None) return
        val now = System.currentTimeMillis()
        if (now - lastAutoCheck < AUTO_INTERVAL_MS) return
        lastAutoCheck = now
        checkNow(ctx.applicationContext, manual = false)
    }

    // 설정에서 수동 확인 (새 버전이면 대화상자)
    fun manualCheck(ctx: Context) = checkNow(ctx.applicationContext, manual = true)

    private fun checkNow(app: Context, manual: Boolean) {
        if (job?.isActive == true) return
        check = CheckState.CHECKING
        job = scope.launch {
            val info = try { fetchInfo() } catch (_: Exception) { null }
            latest = info
            when {
                info == null -> check = CheckState.FAILED
                info.code > currentCode(app) -> {
                    check = CheckState.AVAILABLE
                    step = UpdStep.Available(info)
                }
                else -> {
                    check = CheckState.LATEST
                    clearCache(app)  // 이미 설치된 업데이트 파일 정리
                }
            }
            if (!manual && check == CheckState.FAILED) check = CheckState.NONE
        }
    }

    fun dismiss() {
        if (step is UpdStep.Downloading) job?.cancel()
        step = UpdStep.None
    }

    fun download(ctx: Context, info: UpdateInfo) {
        val app = ctx.applicationContext
        job?.cancel()
        step = UpdStep.Downloading(info, 0f)
        job = scope.launch {
            try {
                val file = downloadApk(app, info) { p -> step = UpdStep.Downloading(info, p) }
                install(app, info, file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                step = UpdStep.Failed(info)
            }
        }
    }

    // '이 출처 허용'을 켜고 돌아왔을 때 (MainActivity.onResume)
    fun onResume(ctx: Context) {
        val s = step
        if (s is UpdStep.NeedPermission && ctx.packageManager.canRequestPackageInstalls()) {
            install(ctx.applicationContext, s.info, s.file)
        } else {
            autoCheck(ctx)
        }
    }

    fun openInstallPermission(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (_: Exception) { }
    }

    private fun install(app: Context, info: UpdateInfo, file: File) {
        if (!app.packageManager.canRequestPackageInstalls()) {
            step = UpdStep.NeedPermission(info, file)
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(i)
            step = UpdStep.None
        } catch (_: Exception) {
            step = UpdStep.Failed(info)
        }
    }

    // ---------- 네트워크 ----------
    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            useCaches = false
            instanceFollowRedirects = true  // github.com → release-assets(https) 리다이렉트
            setRequestProperty("User-Agent", "AGV-RFID-Manager")
            setRequestProperty("Cache-Control", "no-cache")
        }

    private suspend fun fetchInfo(): UpdateInfo = withContext(Dispatchers.IO) {
        val conn = open(VERSION_URL)
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            UpdateInfo(
                code = o.getLong("versionCode"),
                name = o.optString("versionName"),
                url = o.getString("url"),
                notes = o.optString("notes"),
            )
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun downloadApk(app: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "update").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "update.apk")
            val conn = open(info.url)
            try {
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastPct = -1
                        while (true) {
                            ensureActive()  // 취소 시 즉시 중단
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    withContext(Dispatchers.Main) { onProgress(pct / 100f) }
                                }
                            }
                        }
                    }
                }
                if (total > 0 && out.length() != total) throw IOException("incomplete")
            } finally {
                conn.disconnect()
            }
            // 받은 파일 검증: 같은 앱이고 더 새 버전인지
            val pi = app.packageManager.getPackageArchiveInfo(out.path, 0) ?: throw IOException("invalid apk")
            if (pi.packageName != app.packageName) throw IOException("package mismatch")
            if (PackageInfoCompat.getLongVersionCode(pi) <= currentCode(app)) throw IOException("not newer")
            out
        }

    private fun clearCache(app: Context) {
        try { File(app.cacheDir, "update").listFiles()?.forEach { it.delete() } } catch (_: Exception) { }
    }
}
