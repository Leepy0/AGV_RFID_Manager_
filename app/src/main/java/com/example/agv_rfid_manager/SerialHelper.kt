package com.example.agv_rfid_manager

import android.content.Context
import android.hardware.usb.UsbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.collections.get
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class SerialHelper(private val context: Context) {
    var port: UsbSerialPort? = null
    private var readJob: Job? = null
    var msgIdCounter = 0 // TX마다 1씩 가산될 시퀀스

    // UI로 TX/RX 로그를 전달하기 위한 이벤트 스트림
    val logFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)

    // 통신 포트 열기 (기본 115200 bps)
    fun connect(baudRate: Int = 115200): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (drivers.isEmpty()) return false

        val driver = drivers[0]
        val connection = manager.openDevice(driver.device) ?: return false

        port = driver.ports[0]
        return try {
            port?.open(connection)
            port?.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            startReading() // 연결 즉시 수신 대기 시작
            true
        } catch (e: Exception) {
            false
        }
    }

    // 통신 포트 닫기
    fun disconnect() {
        readJob?.cancel()
        port?.close()
        port = null
    }

    // 백그라운드 수신 (RX)
    private fun startReading() {
        readJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            while (isActive) {
                try {
                    val len = port?.read(buffer, 100) ?: 0
                    if (len > 0) {
                        val data = buffer.copyOf(len)
                        val hexString = data.joinToString(" ") { "%02X".format(it) }
                        logFlow.tryEmit("[RX] $hexString")
                    }
                } catch (e: Exception) {
                    logFlow.tryEmit("[System] 수신 에러 또는 케이블 분리됨")
                    break
                }
            }
        }
    }

    // 스마트 패킷 빌더 및 송신 (TX)
    fun sendPacket(agvId: String, actionId: String, actionNum: String) {
        if (port == null) {
            logFlow.tryEmit("[System] 포트가 연결되지 않았습니다.")
            return
        }

        val packet = ByteArray(12)
        packet[0] = 0x02 // STX
        packet[1] = 0x45 // Command 'E'
        packet[2] = 0x00 // PAN ID
        packet[3] = msgIdCounter.toByte()

        // AGV ID (2자리) ASCII 변환
        val agv = agvId.padStart(2, '0').toByteArray(Charsets.US_ASCII)
        packet[4] = agv.getOrElse(0) { 0x30 }
        packet[5] = agv.getOrElse(1) { 0x30 }

        // Action ID (1자리 대문자)
        packet[6] = actionId.firstOrNull()?.code?.toByte() ?: 0x41

        // Action Num (3자리) ASCII 변환
        val num = actionNum.padStart(3, '0').toByteArray(Charsets.US_ASCII)
        packet[7] = num.getOrElse(0) { 0x30 }
        packet[8] = num.getOrElse(1) { 0x30 }
        packet[9] = num.getOrElse(2) { 0x30 }

        // Checksum 연산: Index 1부터 9까지 단순 덧셈
        var checksum = 0
        for (i in 1..9) {
            checksum += packet[i].toUByte().toInt()
        }
        packet[10] = checksum.toByte()
        packet[11] = 0x03 // ETX

        try {
            port?.write(packet, 200)
            val hexString = packet.joinToString(" ") { "%02X".format(it) }
            logFlow.tryEmit("[TX] $hexString")
            msgIdCounter = (msgIdCounter + 1) % 256 // ID 가산
        } catch (e: Exception) {
            logFlow.tryEmit("[System] 전송 실패: ${e.message}")
        }
    }
    // --- [수정된 부분] 텍스트 입력 시 STX, Checksum, ETX 자동 조립 전송 ---
    fun sendTextPacket(textInput: String) {
        if (port == null) {
            logFlow.tryEmit("[System] 포트가 연결되지 않았습니다.")
            return
        }

        try {
            // 입력받은 텍스트를 ASCII 바이트 배열로 변환
            val textBytes = textInput.toByteArray(Charsets.US_ASCII)

            // 전체 패킷 크기 = STX(1) + 텍스트길이 + Checksum(1) + ETX(1)
            val packet = ByteArray(textBytes.size + 3)

            packet[0] = 0x02.toByte() // STX

            // 텍스트 데이터를 패킷의 1번 인덱스부터 복사
            System.arraycopy(textBytes, 0, packet, 1, textBytes.size)

            // Checksum 연산 (텍스트 바이트들의 단순 덧셈)
            var checksum = 0
            for (b in textBytes) {
                checksum += b.toUByte().toInt()
            }
            packet[packet.size - 2] = checksum.toByte() // 마지막에서 두 번째 자리에 Checksum 삽입
            packet[packet.size - 1] = 0x03.toByte() // ETX 삽입

            port?.write(packet, 200)

            // 전송된 실제 바이너리 데이터를 확인하기 위해 Hex로 변환하여 로그 출력
            val hexString = packet.joinToString(" ") { "%02X".format(it) }
            logFlow.tryEmit("[TX Text] $hexString")

        } catch (e: Exception) {
            logFlow.tryEmit("[System] 전송 실패: ${e.message}")
        }
    }
}